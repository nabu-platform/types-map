package be.nabu.libs.types.map;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

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
import be.nabu.libs.types.api.SimpleTypeWrapper;
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
		if (content.containsKey(path)) {
			object = content.get(path);
		}
		else {
			ParsedPath parsedPath = new ParsedPath(path);
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
		// this should only ever occur at the deepest level
		// we don't want the Map to be interpreted as a key/value store (e.g. for xml marshalling)
		if (object instanceof Map) {
			return new MapContent((ComplexType) getType().get(path).getType(), (Map) object);
		}
		return object;
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
}
