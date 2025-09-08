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

import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.Callable;

import be.nabu.libs.types.ComplexContentWrapperFactory;
import be.nabu.libs.types.SimpleTypeWrapperFactory;
import be.nabu.libs.types.api.ComplexContent;
import be.nabu.libs.types.api.ComplexContentWrapper;
import be.nabu.libs.types.api.DefinedSimpleType;
import be.nabu.libs.types.api.ModifiableComplexType;
import be.nabu.libs.types.base.ComplexElementImpl;
import be.nabu.libs.types.base.SimpleElementImpl;
import be.nabu.libs.types.base.TypeBaseUtils;
import be.nabu.libs.types.base.ValueImpl;
import be.nabu.libs.types.properties.MaxOccursProperty;
import be.nabu.libs.types.properties.MinOccursProperty;

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

	public static ModifiableComplexType buildFromContent(Map<String, ?> content) {
		return buildFromContent(content, new MapType());
	}
	
	// can use this method to "enrich" for example in a loop where not all entries have the same fields
	@SuppressWarnings("unchecked")
	public static ModifiableComplexType buildFromContent(Map<String, ?> content, ModifiableComplexType type) {
		type.setName(content.get("$root") instanceof String ? (String) content.get("$root") : "anonymous");
		for (String key : content.keySet()) {
			if ("$root".equals(key)) {
				continue;
			}
			Object value = content.get(key);
			if (value instanceof Object[] || value instanceof Collection || value instanceof Iterable) {
				Iterable<Object> iterable = value instanceof Object[] ? Arrays.asList((Object[]) value) : (Iterable<Object>) value;
				Iterator<Object> iterator = iterable.iterator();
				if (iterator.hasNext()) {
					boolean added = false;
					while(iterator.hasNext()) {
						Object next = iterator.next();
						if (next == null) {
							continue;
						}
						if (next instanceof Callable) {
							try {
								next = ((Callable) next).call();
							}
							catch (Exception e) {
								throw new RuntimeException(e);
							}
						}
						addToType(type, key, next, true);
						added = true;
					}
					if (!added) {
						addToType(type, key, "unknown", true);	
					}
				}
				else {
					// add it as a string
					addToType(type, key, "unknown", true);
				}
			}
			else if (value != null) {
				addToType(type, key, value, false);
			}
			// if we have an empty "$value", it is for a complex simle type and should be a string
			else if (value == null && key.equals("$value")) {
				addToType(type, key, "", false);
			}
		}
		return type;
	}

	@SuppressWarnings({ "unchecked" })
	private static void addToType(ModifiableComplexType type, String key, Object value, boolean inList) {
		DefinedSimpleType<? extends Object> wrap = SimpleTypeWrapperFactory.getInstance().getWrapper().wrap(value.getClass());
		if (wrap != null) {
			// only add if it doesn't exist yet!
			if (type.get(key) == null) {
				// we don't know whether it is mandatory or not, but we'll assume not
				type.add(new SimpleElementImpl(key, wrap, type, new ValueImpl<Integer>(MaxOccursProperty.getInstance(), inList ? 0 : 1), new ValueImpl<Integer>(MinOccursProperty.getInstance(), 0)));
			}
		}
		else if (value instanceof Map) {
			ModifiableComplexType buildFromContent = buildFromContent((Map) value);
			if (type.get(key) != null) {
				TypeBaseUtils.merge((ModifiableComplexType) type.get(key).getType(), buildFromContent);
			}
			else {
				type.add(new ComplexElementImpl(key, buildFromContent, type, new ValueImpl<Integer>(MaxOccursProperty.getInstance(), inList ? 0 : 1), new ValueImpl<Integer>(MinOccursProperty.getInstance(), 0)));
			}
		}
		else {
			ComplexContent complexContent = ComplexContentWrapperFactory.getInstance().getWrapper().wrap(value);
			if (complexContent != null) {
				if (type.get(key) != null) {
					TypeBaseUtils.merge((ModifiableComplexType) type.get(key).getType(), complexContent.getType());
				}
				else {
					type.add(new ComplexElementImpl(key, complexContent.getType(), type, new ValueImpl<Integer>(MaxOccursProperty.getInstance(), inList ? 0 : 1), new ValueImpl<Integer>(MinOccursProperty.getInstance(), 0)));
				}
			}
		}
	}

}
