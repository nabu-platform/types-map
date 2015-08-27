package be.nabu.libs.types.map;

import java.util.HashMap;
import java.util.Map;

import be.nabu.libs.types.api.ComplexContent;
import be.nabu.libs.types.api.ModifiableComplexType;
import be.nabu.libs.types.base.BaseComplexType;

@SuppressWarnings("rawtypes")
public class MapType extends BaseComplexType<Map> implements ModifiableComplexType {

	@Override
	public ComplexContent newInstance() {
		return new MapContent(this, new HashMap<String, Object>());
	}

}
