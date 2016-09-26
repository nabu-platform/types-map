package be.nabu.libs.types.map;

import be.nabu.libs.types.api.ModifiableComplexType;
import be.nabu.libs.types.api.ModifiableComplexTypeGenerator;

public class MapTypeGenerator implements ModifiableComplexTypeGenerator {

	private boolean literal;
	
	public MapTypeGenerator() {
		this(false);
	}
	
	public MapTypeGenerator(boolean literal) {
		this.literal = literal;
	}
	
	@Override
	public ModifiableComplexType newComplexType() {
		MapType mapType = new MapType(literal);
		mapType.setName("anonymous");
		return mapType;
	}

}
