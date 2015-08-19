package be.nabu.libs.types.map;

import be.nabu.libs.types.api.ModifiableComplexType;
import be.nabu.libs.types.api.ModifiableComplexTypeGenerator;

public class MapTypeGenerator implements ModifiableComplexTypeGenerator {

	@Override
	public ModifiableComplexType newComplexType() {
		return new MapType();
	}

}
