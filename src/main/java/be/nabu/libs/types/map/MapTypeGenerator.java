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

import be.nabu.libs.types.api.ModifiableComplexType;
import be.nabu.libs.types.api.ModifiableComplexTypeGenerator;

public class MapTypeGenerator implements ModifiableComplexTypeGenerator {

	private boolean literal;
	private boolean wrapMaps;
	
	public MapTypeGenerator() {
		this(false);
	}
	
	public MapTypeGenerator(boolean literal) {
		this.literal = literal;
	}
	
	@Override
	public ModifiableComplexType newComplexType() {
		MapType mapType = new MapType(literal);
		mapType.setWrapMaps(wrapMaps);
		mapType.setName("anonymous");
		return mapType;
	}

	public boolean isWrapMaps() {
		return wrapMaps;
	}
	public void setWrapMaps(boolean wrapMaps) {
		this.wrapMaps = wrapMaps;
	}

}
