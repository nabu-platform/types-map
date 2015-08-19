package be.nabu.libs.types.map;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import be.nabu.libs.property.ValueUtils;
import be.nabu.libs.property.api.Value;
import be.nabu.libs.types.api.ComplexContent;
import be.nabu.libs.types.api.Element;
import be.nabu.libs.types.api.Group;
import be.nabu.libs.types.api.ModifiableComplexType;
import be.nabu.libs.types.base.BaseType;
import be.nabu.libs.types.base.ValueImpl;
import be.nabu.libs.types.properties.AttributeQualifiedDefaultProperty;
import be.nabu.libs.types.properties.ElementQualifiedDefaultProperty;
import be.nabu.libs.types.properties.NameProperty;
import be.nabu.libs.types.properties.NamespaceProperty;
import be.nabu.libs.validator.api.ValidationMessage;
import be.nabu.libs.validator.api.ValidationMessage.Severity;

@SuppressWarnings("rawtypes")
public class MapType extends BaseType<Map> implements ModifiableComplexType {

	private List<Group> groups = new ArrayList<Group>();
	private Map<String, Element<?>> elements = new HashMap<String, Element<?>>();
	
	@Override
	public String getName(Value<?>...values) {
		return ValueUtils.getValue(NameProperty.getInstance(), values);
	}

	@Override
	public String getNamespace(Value<?>...values) {
		return ValueUtils.getValue(NamespaceProperty.getInstance(), values);
	}

	@Override
	public Iterator<Element<?>> iterator() {
		return elements.values().iterator();
	}

	@Override
	public Element<?> get(String arg0) {
		return elements.get(arg0);
	}

	@Override
	public Group[] getGroups() {
		return groups.toArray(new Group[groups.size()]);
	}

	@Override
	public Boolean isAttributeQualified(Value<?>...values) {
		return ValueUtils.getValue(AttributeQualifiedDefaultProperty.getInstance(), values);
	}

	@Override
	public Boolean isElementQualified(Value<?>...values) {
		return ValueUtils.getValue(ElementQualifiedDefaultProperty.getInstance(), values);
	}

	@Override
	public ComplexContent newInstance() {
		return new MapContent(this, new HashMap<String, Object>());
	}

	@Override
	public List<ValidationMessage> add(Element<?> element) {
		List<ValidationMessage> messages = new ArrayList<ValidationMessage>();
		if (elements.containsKey(element.getName())) {
			messages.add(new ValidationMessage(Severity.ERROR, "An element already exists with the name: " + element.getName()));
		}
		else {
			elements.put(element.getName(), element);
		}
		return messages;
	}

	@Override
	public List<ValidationMessage> add(Group group) {
		groups.add(group);
		return new ArrayList<ValidationMessage>();
	}

	@Override
	public void remove(Element<?> name) {
		elements.remove(name.getName());
	}

	@Override
	public void remove(Group group) {
		groups.remove(group);
	}

	@Override
	public void setName(String value) {
		setProperty(new ValueImpl<String>(NameProperty.getInstance(), value));
	}

	@Override
	public void setNamespace(String value) {
		setProperty(new ValueImpl<String>(NamespaceProperty.getInstance(), value));
	}

}
