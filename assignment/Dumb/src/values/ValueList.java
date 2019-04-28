package values;

import java.util.ArrayList;
import java.util.stream.Collectors;

import interpreter.ExceptionSemantic;

/**
 * @see https://docs.oracle.com/javase/8/docs/api/index.html?java/util/ArrayList.html
 * @author amrwc
 */
public class ValueList extends ValueAbstract {

	private ArrayList<Value> internalValue = new ArrayList<Value>();
	
	public ValueList() {}
	
	public String getName() {
		return "List";
	}

	public int compare(Value v) {
		ArrayList<Value> arr = ((ValueList) v).internalValue;

		if (internalValue.equals(arr))
			return 0;
		else
			return 1;
	}

	/**
	 * Execute a prototype function.
	 * 
	 * @param protoFunc
	 * @param protoArg
	 * @return Value
	 * @author amrwc
	 */
	public Value execProto(String protoFunc, Value protoArg) {
		switch (protoFunc) {
			case "append":
				append(protoArg);
				break;
			case "length":
				return length();
			default:
				throw new ExceptionSemantic("There is no prototype function \"" + protoFunc + "\" in ValueList class.");
		}

		return null;
	}

	public void append(Value v) {
		if (v == null) throw new ExceptionSemantic("The argument for ValueList.append() cannot be null.");
		internalValue.add(v);
	}
	
	public Value get(int i) {
		if (internalValue.size() <= i)
			throw new ExceptionSemantic("The index " + i + " is out of bounds of the list with length "
			+ internalValue.size() + ".");

		Value val = internalValue.get(i);
		if (val != null) return val;
		throw new ExceptionSemantic("Value of index " + i + " in the list is undefined or equal to null.");
	}

	public void set(int i, Value v) {
		if (i < 0) throw new ExceptionSemantic("The index in ValueList cannot be negative.");
		if (v == null) throw new ExceptionSemantic("The Value passed into ValueList.set() cannot be null.");
		internalValue.set(i, v);
	}

	// To be used in Dumb-lang because it resolves to a Value.
	private Value length() {
		return new ValueInteger((long) internalValue.size());
	}

	// To be used internally, by the Parser implementation.
	public int size() {
		return internalValue.size();
	}

	public String toString() {
		// https://stackoverflow.com/a/23183963/10620237
		String strVal = internalValue
							.stream()
							.map(Object::toString)
							.collect(Collectors.joining(", "));
		return "[" + strVal + "]";
	}
}
