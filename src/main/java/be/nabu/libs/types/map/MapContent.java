/*
* Copyright (C) 2015 Alexander Verbruggen
*
* This program is free software: you can redistribute it and/or modify
* it under the terms of the GNU Lesser General Public License as published by
* the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
* GNU Lesser General Public License for more details.
*
* You should have received a copy of the GNU Lesser General Public License
* along with this program. If not, see <https://www.gnu.org/licenses/>.
*/

package be.nabu.libs.types.map;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import be.nabu.libs.types.CollectionHandlerFactory;
import be.nabu.libs.types.ComplexContentWrapperFactory;
import be.nabu.libs.types.ParsedPath;
import be.nabu.libs.types.SimpleTypeWrapperFactory;
import be.nabu.libs.types.api.CollectionHandlerProvider;
import be.nabu.libs.types.api.ComplexContent;
import be.nabu.libs.types.api.ComplexType;
import be.nabu.libs.types.api.DefinedSimpleType;
import be.nabu.libs.types.api.Element;
import be.nabu.libs.types.api.ModifiableComplexType;
import be.nabu.libs.types.base.ComplexElementImpl;
import be.nabu.libs.types.base.SimpleElementImpl;
import be.nabu.libs.types.base.ValueImpl;
import be.nabu.libs.types.properties.MaxOccursProperty;
import be.nabu.libs.types.properties.MinOccursProperty;

public class MapContent implements ComplexContent {

	@SuppressWarnings("rawtypes")
	private Map content;
	private ComplexType type;
	private boolean literal;
	private boolean allowNewFields = true;
	private boolean wrapMaps = false;
	
	public MapContent() {
		this(new MapType(), new HashMap<String, Object>());
	}
	
	public MapContent(ComplexType type, Map<String, ?> content) {
		this(type, content, false);
	}

	public MapContent(ComplexType type, Map<String, ?> content, boolean literal) {
		this.type = type;
		this.content = content;
		this.literal = literal;
	}
	
	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Override
	public Object get(String path) {
		ParsedPath parsedPath = null;
		Object object;
		if (content.containsKey(path)) {
			object = content.get(path);
		}
		else {
			parsedPath = new ParsedPath(path);
			object = content.get(parsedPath.getName());
			if (object != null && parsedPath.getIndex() != null) {
				CollectionHandlerProvider handler = CollectionHandlerFactory.getInstance().getHandler().getHandler(object.getClass());
				if (handler == null) {
					throw new IllegalArgumentException("No collection handler for: " + object);
				}
				object = handler.get(object, handler.unmarshalIndex(parsedPath.getIndex(), object));
			}
			if (object != null && parsedPath.getChildPath() != null) {
				if (object instanceof ComplexContent) {
					object = ((ComplexContent) object).get(parsedPath.getChildPath().toString());
				}
				else {
					object = ComplexContentWrapperFactory.getInstance().getWrapper().wrap(object).get(parsedPath.getChildPath().toString());
				}
			}
		}
		
		// in glue maps are handled as flat key/value pairs
		// that means if we want to do object-access (e.g. we do a set("configuration/properties", something), it actually uses the full path as the key, rather than nesting maps
		// this means, if we xml.objectify() something and we get plain maps back, object access might malfunction, which is what happened during deployment where "configuration/properties" was seen as a single key in a statement "part/configuration/properties = keyValuePairs"
		// this in turn was not correctly marshalled back into an XML and it was quietly ignored
		// there was in fact an object "configuration" which already had a subobject "properties", but the flat map handling does not recognize this
		// for those cases, it is better to autowrap maps into mapcontent, which enables complex handling
		// however, some other modules (notably the swagger provider) builds correct hierarchical maps but does not do well when the typing is enforced through mapcontent
		// instead it works with maps and presumably does a MapContentWrapper wrapping which dynamically generates a new type based on the map rather than using the centralized type available in map content
		// because swagger is so fked up definition wise, there is no single type that can actually describe it
		// however, specifically because we auto-wrap maps and _don't_ want the behavior there, it is disabled by default
		// the only real known problem with maps is when path-like keys are not enforced to be hierarchic
		// also, if we force the map wrapping, it seems derive() + json serialization breaks in glue, we get empty objects (very similar to the empty objects in the swagger). it is not yet fully understood how this is related but we'll leave it like this for now
		if (!wrapMaps) {
			return object;
		}
		// we _do_ have complex keys at which point we should hit the first branch
		// we could verify this further, but for now we leave it like this
		Element<?> element = getType().get(path);
		if (element == null && parsedPath != null) {
			element = getType().get(parsedPath.getName());
		}
		if (element != null && element.getType() instanceof ComplexType) {
			// this should only ever occur at the deepest level
			// we don't want the Map to be interpreted as a key/value store (e.g. for xml marshalling)
			if (object instanceof Map) {
				// we'll assume (for now) that the accompanying type is correct and does not need local enriching, at least not in the get(), it should already be automatically enriched during the set
//					object = copy((ComplexType) element.getType(), (Map) object);
				object = new MapContent((ComplexType) element.getType(), (Map) object);
				// inherit the wrap maps so child content also enforces this
				((MapContent) object).wrapMaps = true;
			}
			// we might have an iteration of maps
			else if (object instanceof Iterable && element.getType().isList(element.getProperties())) {
				List result = new ArrayList();
				for (Object single : (Iterable) object) {
					if (single instanceof Map) {
						single = new MapContent((ComplexType) element.getType(), (Map) single);
						((MapContent) single).wrapMaps = true;
						result.add(single);
//							result.add(copy((ComplexType) element.getType(), (Map) single));
					}
					else {
						result.add(single);
					}
				}
				object = result;
			}
		}
		return object;
	}
	// this basically forces the complextype (assuming its a maptype) to be updated because the mapcontent will update the maptype as it sees new values
	// so basically its a lazy reuse of that logic
	private MapContent copy(ComplexType type, Map map) {
		MapContent result = new MapContent(type, new HashMap());
		Set<Map.Entry<String, Object>> entrySet = map.entrySet();
		for (Map.Entry<String, Object> entry : entrySet) {
			result.set(entry.getKey(), entry.getValue());
		}
		return new MapContent(type, map);
	}
	
	@Override
	public ComplexType getType() {
		return type;
	}
	
	@Override
	public boolean has(String path) {
		// TODO: use parsedpath and recurse
		return content != null && content.containsKey(path);
	}
	
	@Override
	public void delete(String path) {
		// TODO: use parsedpath and recurse
		content.remove(path);
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Override
	public void set(String path, Object value) {
		// when setting a value directly in this instance (so not a child instance), we need to check that our type definition is still in sync
		// note that structure instances are aimed at type safety: constraining the runtime to adhere to the definition
		// map contents are different, they are aimed at flexibility: letting the runtime change the definition at hoc
		boolean recalculateType = false;
		if (literal && type.get(path) != null) {
			content.put(path, value);
		}
		else {
			ParsedPath parsedPath = new ParsedPath(path);
	
			// delegate the setting to another object
			if (parsedPath.getChildPath() != null) {
				ParsedPath pathToSet = parsedPath.getChildPath();
				parsedPath.setChildPath(null);
				// we need to get the last part of the path
//				while (parsedPath.getChildPath() != null) {
//					if (parsedPath.getChildPath().getChildPath() == null) {
//						pathToSet = parsedPath.getChildPath();
//						parsedPath.setChildPath(null);
//						break;
//					}
//				}
				Object targetObject = get(parsedPath.toString());
				if (targetObject == null) {
					Element<?> element = getType().get(parsedPath.getName());
					if (element == null) {
						if (getType() instanceof ModifiableComplexType && allowNewFields) {
							MapType mapType = new MapType();
							element = new ComplexElementImpl(parsedPath.toString(), mapType, getType(), new ValueImpl<Integer>(MinOccursProperty.getInstance(), 0));
							((ModifiableComplexType) getType()).add(element);
						}
						else {
							throw new IllegalStateException("Could not find: " + parsedPath.toString() + " in path: " + path);
						}
					}
					if (!(element.getType() instanceof ComplexType)) {
						throw new IllegalStateException("The field is not complex: " + parsedPath.toString() + " in path " + path);
					}
					targetObject = ((ComplexType) element.getType()).newInstance();
					content.put(parsedPath.getName(), targetObject);
				}
				if (!(targetObject instanceof ComplexContent)) {
					targetObject = ComplexContentWrapperFactory.getInstance().getWrapper().wrap(targetObject);
				}
				((ComplexContent) targetObject).set(pathToSet.toString(), value);
			}
			else if (parsedPath.getIndex() != null) {
				recalculateType = true;
				Object object = get(parsedPath.getName());
				if (object == null) {
					object = new ArrayList();
				}
				CollectionHandlerProvider handler = CollectionHandlerFactory.getInstance().getHandler().getHandler(object.getClass());
				if (handler == null) {
					throw new IllegalArgumentException("No collection handler for: " + object);
				}
				object = handler.set(object, handler.unmarshalIndex(parsedPath.getIndex(), object), value);
				content.put(parsedPath.getName(), object);
			}
			else {
				recalculateType = true;
				content.put(parsedPath.getName(), value);
				// we update the type to have this new field, otherwise we might not be able to access it later (e.g. for marshalling)
			}
			// if we want to recalculate the type, let's do so
			if (recalculateType && type instanceof ModifiableComplexType) {
				value = content.get(parsedPath.getName());
				// can only recalculate with a value
				if (value != null) {
					Element<?> originalElement = type.get(parsedPath.getName());
					// remove the original type, we is recalculating
					if (originalElement != null) {
						((ModifiableComplexType) type).remove(originalElement);
					}
					// @2025-08-25: if you first parse an xml with xml.objectify, then dynamically add a new complex field with a regular assign (so not a structure extension which actually creates a new type)
					// this new field (which is a map) gets picked up by the collection handler with a StringMapCollectionHandler
					// this means, this then starts to fail as it is treated as a string
					// i _think_ we want all maps set as dynamic new content to be treated as a map container rather than a collection, but for now I will limit it to this particular boolean which already exists and is set to true in xml.objectify
	//					boolean dynamicallyWrapMaps = wrapMaps;
					// there are plenty of places (e.g. json.objectify) that do not set the wrapMaps boolean...so currently assuming that all dynamically added content must pass through here
					// hard to gauge backwards compatibility of this change
					// maps get special treatment...
					if (value instanceof Map || value instanceof MapContent) {
						Map<String, ?> map = value instanceof Map ? (Map) value : ((MapContent) value).getContent();
						ModifiableComplexType dynamicMapType = originalElement != null && originalElement.getType() instanceof ModifiableComplexType ? MapContentWrapper.buildFromContent(map, (ModifiableComplexType) originalElement.getType()) : MapContentWrapper.buildFromContent(map);
						((ModifiableComplexType) type).add(new ComplexElementImpl(parsedPath.getName(), dynamicMapType, type, new ValueImpl<Integer>(MinOccursProperty.getInstance(), 0), new ValueImpl<Integer>(MaxOccursProperty.getInstance(), 1)));
						if (value instanceof Map) {
							// we have now added a Map, however any dynamic changes to that map later on (e.g. new keys) will NOT trigger a re-evaluation of the generated type
							// to force that, we wrap the original map in a mapcontent
							MapContent mapContent = new MapContent(dynamicMapType, (Map<String, ?>) value);
							// inherit!
							mapContent.wrapMaps = wrapMaps;
							// overwrite with wrapped
							content.put(parsedPath.getName(), mapContent);
						}
						// otherwise, we already have mapcontent, just update the type
						else {
							((MapContent) value).type = dynamicMapType;
						}
					}
					else if (value instanceof ComplexContent) {
						((ModifiableComplexType) type).add(new ComplexElementImpl(parsedPath.getName(), ((ComplexContent) value).getType(), type, new ValueImpl<Integer>(MinOccursProperty.getInstance(), 0), new ValueImpl<Integer>(MaxOccursProperty.getInstance(), 1)));
					}
					else {
						CollectionHandlerProvider handler = CollectionHandlerFactory.getInstance().getHandler().getHandler(value.getClass());
						Class<?> clazz = null;
						// inherit from existing element to expand upon it if possible
						ModifiableComplexType dynamicMapType = originalElement != null && originalElement.getType() instanceof ModifiableComplexType ? (ModifiableComplexType) originalElement.getType() : null;
						ComplexType defaultComplexType = null;
						List<Object> wrappedList = new ArrayList<Object>();
						if (handler == null) {
							clazz = value.getClass();
						}
						else {
							// if the children are maps, do a dynamic type
							// a list of wrapped elements so we don't expose Map as is but wrap it in MapContent to get notified of future dynamic changes
							// we always build a secondary map because we don't know at which point we might encounter a map
							for (Object object : handler.getAsIterable(value)) {
								if (object != null) {
									if (object instanceof Map) {
										dynamicMapType = MapContentWrapper.buildFromContent((Map<String, ?>) object, dynamicMapType == null ? new MapType() : dynamicMapType);
										MapContent mapContent = new MapContent(dynamicMapType, (Map<String, ?>) object);
										// inherit!
										mapContent.wrapMaps = wrapMaps;
										wrappedList.add(mapContent);
									}
									// TODO: we currently don't check for consistency like making sure they share a common parent type etc, we just take "the last one" in this case
									else if (object instanceof ComplexContent) {
										defaultComplexType = ((ComplexContent) object).getType();
										wrappedList.add(object);
									}
									else {
										wrappedList.add(object);
										clazz = object.getClass();
									}
								}
								// even nulls need to be maintained to make sure the iterable is consistent
								else {
									wrappedList.add(object);
								}
							}
						}
						if (dynamicMapType != null) {
							((ModifiableComplexType) type).add(new ComplexElementImpl(parsedPath.getName(), dynamicMapType, type, new ValueImpl<Integer>(MinOccursProperty.getInstance(), 0), new ValueImpl<Integer>(MaxOccursProperty.getInstance(), handler == null ? 1 : 0)));
							// if we created a new list of wrapped elements, set it
							if (handler != null) {
								// we update the collection we already set
								content.put(parsedPath.getName(), wrappedList);
							}
						}
						else if (defaultComplexType != null) {
							((ModifiableComplexType) type).add(new ComplexElementImpl(parsedPath.getName(), defaultComplexType, type, new ValueImpl<Integer>(MinOccursProperty.getInstance(), 0), new ValueImpl<Integer>(MaxOccursProperty.getInstance(), handler == null ? 1 : 0)));
						}
						else {
							// we add the ParameterizedType check, otherwise, if you are using raw lists, the component type thingy will throw an exception
							if (clazz == null && value.getClass() instanceof Type && ((Type) value.getClass()) instanceof ParameterizedType) {
								clazz = handler.getComponentType(value.getClass());
							}
							// currently if we can't determine what is in there, we could either opt for Object.class or String.class
							// however, because the following code is focused on simple types, we use string atm
							// in the future we could make this more complex
							else {
								clazz = String.class;
							}
							DefinedSimpleType<? extends Object> wrap = SimpleTypeWrapperFactory.getInstance().getWrapper().wrap(clazz);
							if (wrap != null) {
								((ModifiableComplexType) type).add(new SimpleElementImpl(parsedPath.getName(), wrap, type, new ValueImpl<Integer>(MinOccursProperty.getInstance(), 0), new ValueImpl<Integer>(MaxOccursProperty.getInstance(), handler == null ? 1 : 0)));
							}
							else {
								// TODO: check for complex types like java beans that are not autowrapped to complex content but are not simple types?
								throw new RuntimeException("Not supported for: " + parsedPath.getName());
							}
						}
					}
				}
			}
		}
	}

	@SuppressWarnings("rawtypes")
	public Map getContent() {
		return content;
	}
	
	@Override
	public String toString() {
		return content.toString();
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public Map toMap() {
		Map map = new LinkedHashMap();
		for (Map.Entry entry : (Set<Map.Entry>) content.entrySet()) {
			if (entry.getValue() instanceof MapContent) {
				map.put(entry.getKey(), ((MapContent) entry.getValue()).toMap());
			}
			else {
				map.put(entry.getKey(), entry.getValue());
			}
		}
		return map;
	}

	public boolean isWrapMaps() {
		return wrapMaps;
	}

	public void setWrapMaps(boolean wrapMaps) {
		this.wrapMaps = wrapMaps;
	}

	public void setType(ComplexType type) {
		this.type = type;
	}
	
}
