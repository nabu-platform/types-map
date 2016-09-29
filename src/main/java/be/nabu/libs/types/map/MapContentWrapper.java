package be.nabu.libs.types.map;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import be.nabu.libs.types.ComplexContentWrapperFactory;
import be.nabu.libs.types.SimpleTypeWrapperFactory;
import be.nabu.libs.types.api.ComplexContent;
import be.nabu.libs.types.api.ComplexContentWrapper;
import be.nabu.libs.types.api.DefinedSimpleType;
import be.nabu.libs.types.base.ComplexElementImpl;
import be.nabu.libs.types.base.SimpleElementImpl;
import be.nabu.libs.types.base.ValueImpl;
import be.nabu.libs.types.properties.MaxOccursProperty;

@SuppressWarnings("rawtypes")
public class MapContentWrapper implements ComplexContentWrapper<Map> {

	@SuppressWarnings("unchecked")
	@Override
	public ComplexContent wrap(Map instance) {
		return new MapContent(buildFromContent(instance), instance);
	}

	@Override
	public Class<Map> getInstanceClass() {
		return Map.class;
	}

	@SuppressWarnings("unchecked")
	public static MapType buildFromContent(Map<String, ?> content) {
		MapType type = new MapType();
		type.setName("anonymous");
		for (String key : content.keySet()) {
			Object value = content.get(key);
			if (value instanceof Object[] || value instanceof Collection) {
				List<Object> values = value instanceof Object[] ? Arrays.asList((Object[]) value) : new ArrayList<Object>((Collection<Object>) value);
				if (!values.isEmpty()) {
					addToType(type, key, values.get(0), true);
				}
				else {
					// add it as a string
					addToType(type, key, "unknown", true);
				}
			}
			else if (value != null) {
				addToType(type, key, value, false);
			}
		}
		return type;
	}

	@SuppressWarnings({ "unchecked" })
	private static void addToType(MapType type, String key, Object value, boolean inList) {
		DefinedSimpleType<? extends Object> wrap = SimpleTypeWrapperFactory.getInstance().getWrapper().wrap(value.getClass());
		if (wrap != null) {
			type.add(new SimpleElementImpl(key, wrap, type, new ValueImpl<Integer>(MaxOccursProperty.getInstance(), inList ? 0 : 1)));
		}
		else if (value instanceof Map) {
			type.add(new ComplexElementImpl(key, buildFromContent((Map) value), type, new ValueImpl<Integer>(MaxOccursProperty.getInstance(), inList ? 0 : 1)));
		}
		else {
			ComplexContent complexContent = ComplexContentWrapperFactory.getInstance().getWrapper().wrap(value);
			if (complexContent != null) {
				type.add(new ComplexElementImpl(key, complexContent.getType(), type, new ValueImpl<Integer>(MaxOccursProperty.getInstance(), inList ? 0 : 1)));
			}
		}
	}

}
