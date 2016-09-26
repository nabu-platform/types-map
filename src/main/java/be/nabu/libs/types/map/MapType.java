package be.nabu.libs.types.map;

import java.util.LinkedHashMap;
import java.util.Map;

import be.nabu.libs.types.api.ComplexContent;
import be.nabu.libs.types.api.ModifiableComplexType;
import be.nabu.libs.types.base.BaseComplexType;

@SuppressWarnings("rawtypes")
public class MapType extends BaseComplexType<Map> implements ModifiableComplexType {

	private boolean literal;
	
	public MapType() {
		this(false);
	}
	
	public MapType(boolean literal) {
		this.literal = literal;
	}
	
	@Override
	public ComplexContent newInstance() {
		return new MapContent(this, new LinkedHashMap<String, Object>(), literal);
	}

}
