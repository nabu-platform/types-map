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
		Object object;
		ParsedPath parsedPath = new ParsedPath(path);
		if (content.containsKey(path)) {
			object = content.get(path);
		}
		else {
			object = content.get(parsedPath.getName());
			if (object != null && parsedPath.getIndex() != null) {
				CollectionHandlerProvider handler = CollectionHandlerFactory.getInstance().getHandler().getHandler(object.getClass());
				if (handler == null) {
					throw new IllegalArgumentException("No collection handler for: " + object);
				}
				object = handler.get(object, handler.unmarshalIndex(parsedPath.getIndex()));
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
		if (!wrapMaps) {
			return object;
		}
		// we _do_ have complex keys at which point we should hit the first branch
		// we could verify this further, but for now we leave it like this
		Element<?> element = getType().get(path);
		if (element == null) {
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

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Override
	public void set(String path, Object value) {
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
				Object object = get(parsedPath.getName());
				if (object == null) {
					object = new ArrayList();
				}
				CollectionHandlerProvider handler = CollectionHandlerFactory.getInstance().getHandler().getHandler(object.getClass());
				if (handler == null) {
					throw new IllegalArgumentException("No collection handler for: " + object);
				}
				object = handler.set(object, handler.unmarshalIndex(parsedPath.getIndex()), value);
				content.put(parsedPath.getName(), object);
			}
			else {
				content.put(parsedPath.getName(), value);
				// we update the type to have this new field, otherwise we might not be able to access it later (e.g. for marshalling)
				if (value != null && type instanceof MapType && type.get(parsedPath.getName()) == null) {
					CollectionHandlerProvider handler = CollectionHandlerFactory.getInstance().getHandler().getHandler(value.getClass());
					Class<?> clazz = null;
					if (handler == null) {
						clazz = value.getClass();
					}
					else {
						for (Object object : handler.getAsIterable(value)) {
							if (object != null) {
								clazz = object.getClass();
								break;
							}
						}
						// we add the ParameterizedType check, otherwise, if you are using raw lists, the component type thingy will throw an exception
						if (clazz == null && value.getClass() instanceof Type && ((Type) value.getClass()) instanceof ParameterizedType) {
							handler.getComponentType(value.getClass());
						}
						// currently if we can't determine what is in there, we could either opt for Object.class or String.class
						// however, because the following code is focused on simple types, we use string atm
						// in the future we could make this more complex
						else {
							clazz = String.class;
						}
					}
					DefinedSimpleType<? extends Object> wrap = SimpleTypeWrapperFactory.getInstance().getWrapper().wrap(clazz);
					if (wrap != null) {
						((MapType) type).add(new SimpleElementImpl(parsedPath.getName(), wrap, type, new ValueImpl<Integer>(MinOccursProperty.getInstance(), 0), new ValueImpl<Integer>(MaxOccursProperty.getInstance(), handler == null ? 1 : 0)));
					}
					else {
						// TODO: check for complex types?
						System.out.println("-----------------_> hitting it!");
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
	
}
