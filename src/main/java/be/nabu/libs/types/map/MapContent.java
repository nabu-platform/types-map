package be.nabu.libs.types.map;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import be.nabu.libs.types.CollectionHandlerFactory;
import be.nabu.libs.types.ComplexContentWrapperFactory;
import be.nabu.libs.types.ParsedPath;
import be.nabu.libs.types.api.CollectionHandlerProvider;
import be.nabu.libs.types.api.ComplexContent;
import be.nabu.libs.types.api.ComplexType;

public class MapContent implements ComplexContent {

	@SuppressWarnings("rawtypes")
	private Map content;
	private ComplexType type;

	public MapContent() {
		this(new MapType(), new HashMap<String, Object>());
	}

	public MapContent(ComplexType type, Map<String, ?> content) {
		this.type = type;
		this.content = content;
	}
	
	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Override
	public Object get(String path) {
		ParsedPath parsedPath = new ParsedPath(path);
		Object object = content.get(parsedPath.getName());
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
		return object;
	}

	@Override
	public ComplexType getType() {
		return type;
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Override
	public void set(String path, Object value) {
		ParsedPath parsedPath = new ParsedPath(path);

		// delegate the setting to another object
		if (parsedPath.getChildPath() != null) {
			ParsedPath pathToSet = parsedPath;
			// we need to get the last part of the path
			while (parsedPath.getChildPath() != null) {
				if (parsedPath.getChildPath().getChildPath() == null) {
					pathToSet = parsedPath.getChildPath();
					parsedPath.setChildPath(null);
					break;
				}
			}
			Object targetObject = get(parsedPath.toString());
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
			handler.set(object, handler.unmarshalIndex(parsedPath.getIndex()), value);
			content.put(parsedPath.getName(), object);
		}
		else {
			content.put(parsedPath.getName(), value);
		}
	}

	@SuppressWarnings("rawtypes")
	public Map getContent() {
		return content;
	}
}
