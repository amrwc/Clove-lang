package interpreter;

import java.util.ArrayList;
import java.util.function.Consumer;

import interpreter.Display.Reference;
import parser.ast.*;
import values.*;

public class Parser implements DumbVisitor {
	
	// Scope display handler
	private Display scope = new Display();
	
	// Get the ith child of a given node.
	private static SimpleNode getChild(SimpleNode node, int childIndex) {
		return (SimpleNode)node.jjtGetChild(childIndex);
	}
	
	// Get the token value of the ith child of a given node.
	private static String getTokenOfChild(SimpleNode node, int childIndex) {
		return getChild(node, childIndex).tokenValue;
	}
	
	// Execute a given child of the given node
	private Object doChild(SimpleNode node, int childIndex, Object data) {
		return node.jjtGetChild(childIndex).jjtAccept(this, data);
	}
	
	// Execute a given child of a given node, and return its value as a Value.
	// This is used by the expression evaluation nodes.
	Value doChild(SimpleNode node, int childIndex) {
		return (Value)doChild(node, childIndex, null);
	}
	
	// Execute all children of the given node
	Object doChildren(SimpleNode node, Object data) {
		return node.childrenAccept(this, data);
	}
	
	// Called if one of the following methods is missing...
	public Object visit(SimpleNode node, Object data) {
		System.out.println(node + ": acceptor not implemented in subclass?");
		return data;
	}
	
	// Execute a Dumb program
	public Object visit(ASTCode node, Object data) {
		return doChildren(node, data);	
	}
	
	// Execute a statement
	public Object visit(ASTStatement node, Object data) {
		return doChildren(node, data);	
	}

	// Execute a block
	public Object visit(ASTBlock node, Object data) {
		return doChildren(node, data);	
	}

	/**
	 * Anonymous object declaration.
	 * 
	 * @author amrwc
	 */
	public Object visit(ASTValueObject node, Object data) {
		// Already defined?
		if (node.optimised != null)
			return data;

		ValueObject valueObject = new ValueObject();

		// Add all the key-value pairs to the anonymous object.
		String keyName;
		Value value;
		for (int i = 0; i < node.jjtGetNumChildren(); i += 2) {
			keyName = getTokenOfChild(node, i);
			value = doChild(node, i + 1);
			valueObject.add(keyName, value);
		}

		node.optimised = valueObject;
		return node.optimised;
	}
	
	/**
	 * List declaration.
	 * 
	 * @author amrwc
	 */
	public Object visit(ASTValueList node, Object data) {
		// Already defined?
		if (node.optimised != null)
			return data;

		ValueList valueList = new ValueList();

		// Add all the values to the list.
		int keyCount = node.jjtGetNumChildren();
		Value currentValue;
		for (int i = 0; i < keyCount; i++) {
			currentValue = doChild(node, i);
			valueList.append(currentValue);
		}

		node.optimised = valueList;
		return node.optimised;
	}

	/**
	 * Anonymous function declaration.
	 * 
	 * @author amrwc
	 */
	public Object visit(ASTFnVal node, Object data) {
		// Already defined?
		if (node.optimised != null)
			return data;

		FunctionDefinition currentFnDef = new FunctionDefinition(scope.getLevel() + 1);

		// Child 0 -- function definition parameter list
		doChild(node, 0, currentFnDef);
		// Child 1 -- function body
		currentFnDef.setFunctionBody(getChild(node, 1));
		// Child 2 -- optional return expression
		if (node.fnHasReturn)
			currentFnDef.setFunctionReturnExpression(getChild(node, 2));

		ValueFn valueFunction = new ValueFn(currentFnDef);

		// Preserve this definition for future reference, and so we don't define
		// it every time this node is processed.
		node.optimised = valueFunction;

		return node.optimised;
	}

	// Function definition
	public Object visit(ASTFnDef node, Object data) {
		// Already defined?
		if (node.optimised != null)
			return data;
		// Child 0 - identifier (fn name)
		String fnname = getTokenOfChild(node, 0);
		if (scope.findFunctionInCurrentLevel(fnname) != null)
			throw new ExceptionSemantic("Function " + fnname + " already exists.");
		FunctionDefinition currentFunctionDefinition = new FunctionDefinition(fnname, scope.getLevel() + 1);
		// Child 1 - function definition parameter list
		doChild(node, 1, currentFunctionDefinition);
		// Add to available functions
		scope.addFunction(currentFunctionDefinition);
		// Child 2 - function body
		currentFunctionDefinition.setFunctionBody(getChild(node, 2));
		// Child 3 - optional return expression
		if (node.fnHasReturn)
			currentFunctionDefinition.setFunctionReturnExpression(getChild(node, 3));
		// Preserve this definition for future reference, and so we don't define
		// it every time this node is processed.
		node.optimised = currentFunctionDefinition;
		return data;
	}
	
	// Function definition parameter list
	public Object visit(ASTParmlist node, Object data) {
		FunctionDefinition currentDefinition = (FunctionDefinition)data;
		for (int i=0; i<node.jjtGetNumChildren(); i++)
			currentDefinition.defineParameter(getTokenOfChild(node, i));
		return data;
	}
	
	// Function body
	public Object visit(ASTFnBody node, Object data) {
		return doChildren(node, data);
	}
	
	// Function return expression
	public Object visit(ASTReturnExpression node, Object data) {
		return doChildren(node, data);
	}
	
	/**
	 * Try finding the ValueFn inside the scope and extract its FunctionDefinition.
	 * 
	 * @author amrwc
	 */
	public FunctionDefinition findValueFn(String fnname) {
		Reference value = scope.findReference(fnname);
		if (value == null)
			throw new ExceptionSemantic("Function " + fnname + " is undefined.");

		ValueFn valueFunction = (ValueFn) value.getValue();
		return valueFunction.get(); // Extract the FunctionDefinition stored in ValueFn.
	}

	/**
	 * Function call.
	 * 
	 * @author amrwc
	 */
	public Object visit(ASTCall node, Object data) {
		FunctionDefinition fndef;
		if (node.optimised == null) { 
			// Child 0 - identifier (fn name)
			String fnname = getTokenOfChild(node, 0);
			fndef = scope.findFunction(fnname);

			if (fndef == null) fndef = findValueFn(fnname);

			// Save it for next time
			node.optimised = fndef;
		} else
			fndef = (FunctionDefinition)node.optimised;

		FunctionInvocation newInvocation = new FunctionInvocation(fndef);
		// Child 1 - arglist
		doChild(node, 1, newInvocation);
		// Execute
		scope.execute(newInvocation, this);
		return data;
	}

	/**
	 * Function invocation in an expression.
	 * 
	 * @author amrwc
	 */
	public Object visit(ASTFnInvoke node, Object data) {
		FunctionDefinition fndef;
		int leftNumChildren = node.jjtGetChild(0).jjtGetNumChildren();

		// NOTE: This locates ValueFn inside of ValueObject.
		// 		 Only works after modifying grammar to dereference() arglist(). 
		// If there's more than 1 child in the left child, it's an object.
		// NOTE: It needs to be added to FnCall as well when it works properly.
		if (leftNumChildren > 0) {
			Value value = doChild(node, 0); // Do the dereference.

			ValueFn valueFunction = (ValueFn) value;
			if (valueFunction == null)
				throw new ExceptionSemantic("The value function you are trying to invoke is undefined.");

//			fndef = scope.findFunctionInCurrentLevel(valueFunction.getName());
			fndef = valueFunction.get();
			if (fndef == null)
				throw new ExceptionSemantic("Function " + valueFunction.getName() + " is undefined.");

			node.optimised = fndef;
		}

		if (node.optimised == null) {
			String fnname = getTokenOfChild(node, 0);
			fndef = scope.findFunction(fnname);

			if (fndef == null) fndef = findValueFn(fnname);

			if (!fndef.hasReturn())
				throw new ExceptionSemantic("Function " + fnname + " is being invoked in an expression but does not have a return value.");

			// Save it for next time
			node.optimised = fndef;
		} else
			fndef = (FunctionDefinition)node.optimised;

		FunctionInvocation newInvocation = new FunctionInvocation(fndef);
		// Child 1 - arglist
		doChild(node, 1, newInvocation);
		// Execute
		return scope.execute(newInvocation, this);
	}

	/**
	 * Prototype function invocation.
	 * 
	 * @author amrwc
	 */
	public Object visit(ASTProtoInvoke node, Object data) {
		Value value = doChild(node, 0);
		String protoFunc = node.tokenValue;
		Value protoArg = (node.jjtGetNumChildren() > 1)
			? parseProtoArg((SimpleNode) node.jjtGetChild(1))
			: null;

		if (value instanceof ValueList) {
			((ValueList) value).execProto(protoFunc, protoArg);
		} else if (value instanceof ValueObject) {
			((ValueObject) value).execProto(protoFunc, protoArg);
		} else {
			throw new ExceptionSemantic(node.tokenValue + " does not support prototype functions.");
		}

		return data;
	}

	/**
	 * Parse the prototype function's arguments.
	 * 
	 * @param node -- proto_invoke() == ASTProtoInvoke
	 * @author amrwc
	 */
	private Value parseProtoArg(SimpleNode node) {
		Value value;

		if (node instanceof ASTIdentifier) {
			Display.Reference ref = scope.findReference(node.tokenValue);
			if (ref == null) throw new ExceptionSemantic("Variable \"" + node.tokenValue + "\" doesn't exist.");
			value = ref.getValue();
		} else {
			value = (Value) node.jjtAccept(this, null);
		}

		if (value == null)
			throw new ExceptionSemantic("The prototype function's argument cannot evaluate to null.");
		return value;
	}

	// Function invocation argument list.
	public Object visit(ASTArgList node, Object data) {
		FunctionInvocation newInvocation = (FunctionInvocation)data;
		for (int i=0; i<node.jjtGetNumChildren(); i++)
			newInvocation.setArgument(doChild(node, i));
		newInvocation.checkArgumentCount();
		return data;
	}

	/**
	 * Execute an if statement.
	 * 
	 * @author amrwc
	 */
	public Object visit(ASTIfStatement node, Object data) {
		Value test = doChild(node, 0);
		if (!(test instanceof ValueBoolean))
			throw new ExceptionSemantic("The test expression of an if statement must be boolean.");

		if (((ValueBoolean) test).booleanValue()) // If test evaluated to true...
			doChild(node, 1);                     // ...do 'if'. Or...
		else if (node.ifHasElse)                  // ...if it evaluated to false and has 'else'...
			doChild(node, 2);                     // ...do 'else'.

		removeDefinitions(node);
		return data;
	}

	/**
	 * Execute a for loop.
	 * 
	 * @author amrwc
	 */
	public Object visit(ASTForLoop node, Object data) {
		doChild(node, 0); // Initialise the loop (usually 'let i = 0').

		while (true) {
			Value loopTest = doChild(node, 1);
			if (!(loopTest instanceof ValueBoolean))
				throw new ExceptionSemantic("The test expression of a for loop must be boolean.");

			if (!((ValueBoolean) loopTest).booleanValue()) // If loopTest evaluated to false, break.
				break;

			doChild(node, 3); // Do the loop statement()/body().

			// Remove the definitions made inside a statement() without block().
			removeDefinitions(node);

			doChild(node, 2); // Evaluate the loop expression (usually 'i++').
		}

		// Remove all definitions in this scope, including the initialisation.
		removeDefinitions(node, (SimpleNode) node.jjtGetChild(0));
		return data;
	}
	
	/**
	 * Execute a while loop.
	 * 
	 * @author amrwc
	 */
	public Object visit(ASTWhileLoop node, Object data) {
		while (true) {
			Value loopTest = doChild(node, 0);
			if (!(loopTest instanceof ValueBoolean))
				throw new ExceptionSemantic("The test expression of a while loop must be boolean.");

			if (!((ValueBoolean) loopTest).booleanValue()) // If loopTest evaluated to false, break.
				break;

			doChild(node, 1); // Do loop statement()/block().
			removeDefinitions(node); // Clean up the variable/function definitions.
		}

		return data;
	}

	/**
	 * Remove all definitions from the scope.
	 * 
	 * @param node
	 * @param init -- initialisation node in for-loops.
	 * @author amrwc
	 */
	public void removeDefinitions(SimpleNode node, SimpleNode init) {
		ArrayList<SimpleNode> definitions = collectDefinitions(node, init);

		Consumer<SimpleNode> removeDefinition = definition -> {
			if (definition instanceof ASTDefinition) {
				SimpleNode assignmentNode = (SimpleNode) definition.jjtGetChild(0);
				String variableName = getTokenOfChild(assignmentNode, 0);
				scope.removeVariable(variableName);
			} else if (definition instanceof ASTFnDef) {
				String fnName = getTokenOfChild(definition, 0);
				scope.removeFunction(fnName);
			}
		};

		definitions.forEach(removeDefinition);
	}

	public void removeDefinitions(SimpleNode node) {
		removeDefinitions(node, null);
	}

	/**
	 * Collect all definitions in the scope.
	 * 
	 * @param node
	 * @param init -- initialisation node in for-loops.
	 * @author amrwc
	 */
	private ArrayList<SimpleNode> collectDefinitions(SimpleNode node, SimpleNode init) {
		ArrayList<SimpleNode> definitions = new ArrayList<SimpleNode>();

		// If it's a for-loop including an initialisation node...
		if (init != null && init instanceof ASTDefinition) definitions.add(init);

		// Handle statement()/block() without children.
		if (node.jjtGetNumChildren() == 0) return definitions;

		// Collect the definitions from the statement()/block().
		Node statement = node.jjtGetChild(node.jjtGetNumChildren() - 1);
		if (statement.jjtGetChild(0) instanceof ASTBlock) {
			// statement() -> block() -> statement() -> definition()+/fndef()+
			Node codeBlock = statement.jjtGetChild(0);
			for (int i = 0; i < codeBlock.jjtGetNumChildren(); i++) {
				Node innerNode = codeBlock.jjtGetChild(i).jjtGetChild(0);
				if (innerNode instanceof ASTDefinition || innerNode instanceof ASTFnDef)
					definitions.add((SimpleNode) innerNode);
			}
		} else {
			// statement() -> definition()/fndef() -- there can only be one, since it's not a block.
			Node innerNode = statement.jjtGetChild(0);
			if (innerNode instanceof ASTDefinition || innerNode instanceof ASTFnDef)
				definitions.add((SimpleNode) innerNode);
		}

		return definitions;
	}

	// Process an identifier
	// This doesn't do anything, but needs to be here because we need an ASTIdentifier node.
	public Object visit(ASTIdentifier node, Object data) {
		return data;
	}

	/**
	 * Execute the WRITE statement. Print out all given arguments.
	 * 
	 * @author amrwc
	 */
	public Object visit(ASTWrite node, Object data) {
		int numChildren = node.jjtGetNumChildren();
		for (int i = 0; i < numChildren; i++) {
			System.out.print(doChild(node, i));
		}
		System.out.println();
		return data;
	}

	/**
	 * Dereference a variable or parameter, and return its value.
	 * 
	 * @author amrwc
	 */
	public Object visit(ASTDereference node, Object data) {
		Display.Reference reference;

		if (node.optimised == null) {
			String name = node.tokenValue;
			reference = scope.findReference(name);
			if (reference == null)
				throw new ExceptionSemantic("Variable or parameter " + name + " is undefined.");
			node.optimised = reference;
		} else
			reference = (Display.Reference) node.optimised;

		int numChildren = node.jjtGetNumChildren();
		if (numChildren > 0) { // If it's not a normal dereference of a variable...
			int currChild = 0; // Keep track of how far it traversed.
			Value value = reference.getValue();

			for (; currChild < numChildren; currChild++) {
				if (value instanceof ValueList)
					value = listDereference(node, value, currChild);
				else if (value instanceof ValueObject)
					value = objectDereference(node, value, currChild);
			}

			return value;
		}

		return reference.getValue();
	}

	/**
	 * Dereference of a list.
	 * 
	 * @author amrwc
	 */
	private Value listDereference(SimpleNode node, Value v, int currChild) {
		ValueList valueList = (ValueList) v;
		int index = (int) ((ValueInteger) doChild(node, currChild)).longValue();
		return valueList.get(index);
	}

	/**
	 * Dereference of an anonymous object.
	 * 
	 * @author amrwc
	 */
	private Value objectDereference(SimpleNode node, Value v, int currChild) {
		ValueObject valueObject = (ValueObject) v;
		String keyName = getTokenOfChild(node, currChild);
		return valueObject.get(keyName);
	}

	/**
	 * Definition using the <LET> keyword.
	 * 
	 * @author amrwc
	 */
	public Object visit(ASTDefinition node, Object data) {
		Display.Reference reference;
		Node assignment = node.jjtGetChild(0);
		String name = getTokenOfChild((SimpleNode) assignment, 0);

		reference = scope.findReference(name);
		if (reference == null)
			reference = scope.defineVariable(name);
		else
			throw new ExceptionSemantic("Variable \"" + name + "\" already exists.");

		// Do the assignment.
		assignment.jjtAccept(this, data);
		return data;
	}

	/**
	 * Execute an assignment statement.
	 * 
	 * @author amrwc
	 */
	public Object visit(ASTAssignment node, Object data) {
		Display.Reference reference;
		int numChildren = node.jjtGetNumChildren();
		Value rightVal = doChild(node, numChildren - 1);

		if (node.optimised == null) {
			String name = getTokenOfChild(node, 0);
			reference = scope.findReference(name);
			if (reference == null)
				throw new ExceptionSemantic("Variable \"" + name + "\" is undefined.");
			node.optimised = reference;
		} else
			reference = (Display.Reference) node.optimised;

		if (node.shorthandOperator != null) {
			Value value = reference.getValue();

			switch (node.shorthandOperator) {
				case "+=":
					reference.setValue(value.add(rightVal));
					break;
				case "-=":
					reference.setValue(value.subtract(rightVal));
			}
			return data;
		}

		if (numChildren > 2) {
			int currChild = 1; // Keep track of how far it traversed.
			Value value = reference.getValue();

			// Dereference
			for (; currChild < numChildren - 2; currChild++) {
				if (value instanceof ValueList)
					value = listDereference(node, value, currChild);
				else if (value instanceof ValueObject)
					value = objectDereference(node, value, currChild);
			}

			// Assignment
			if (value instanceof ValueList) {
				int index = (int) ((ValueInteger) doChild(node, currChild)).longValue();
				((ValueList) value).set(index, rightVal);
			} else if (value instanceof ValueObject) {
				String keyName = getTokenOfChild(node, currChild);
				((ValueObject) value).set(keyName, rightVal);
			}

			return data;
		}

		reference.setValue(rightVal);
		return data;
	}

	/**
	 * Post-increment/decrement.
	 * 
	 * @author amrwc
	 */
	public Object visit(ASTPostfixExpression node, Object data) {
		return shortIncDec(node);
	}

	/**
	 * Pre-increment/decrement.
	 * 
	 * @author amrwc
	 */
	public Object visit(ASTPrefixExpression node, Object data) {
		return shortIncDec(node);
	}

	/**
	 * Increment/decrement.
	 * 
	 * @author amrwc
	 */
	private Value shortIncDec(SimpleNode node) {
		Display.Reference reference;

		String name = getTokenOfChild(node, 0);
		reference = scope.findReference(name);
		if (reference == null)
			throw new ExceptionSemantic("Variable \"" + name + "\" is undefined.");

		Value value = reference.getValue();
		ValueInteger one = new ValueInteger(1);

		switch (node.shorthandOperator) {
			case "++":
				reference.setValue(value.add(one));
				break;
			case "--":
				reference.setValue(value.subtract(one));
		}

		return reference.getValue();
	}

	// OR
	public Object visit(ASTOr node, Object data) {
		return doChild(node, 0).or(doChild(node, 1));
	}

	// AND
	public Object visit(ASTAnd node, Object data) {
		return doChild(node, 0).and(doChild(node, 1));
	}

	// ==
	public Object visit(ASTCompEqual node, Object data) {
		return doChild(node, 0).eq(doChild(node, 1));
	}

	// !=
	public Object visit(ASTCompNequal node, Object data) {
		return doChild(node, 0).neq(doChild(node, 1));
	}

	// >=
	public Object visit(ASTCompGTE node, Object data) {
		return doChild(node, 0).gte(doChild(node, 1));
	}

	// <=
	public Object visit(ASTCompLTE node, Object data) {
		return doChild(node, 0).lte(doChild(node, 1));
	}

	// >
	public Object visit(ASTCompGT node, Object data) {
		return doChild(node, 0).gt(doChild(node, 1));
	}

	// <
	public Object visit(ASTCompLT node, Object data) {
		return doChild(node, 0).lt(doChild(node, 1));
	}

	// +
	public Object visit(ASTAdd node, Object data) {
		return doChild(node, 0).add(doChild(node, 1));
	}

	// -
	public Object visit(ASTSubtract node, Object data) {
		return doChild(node, 0).subtract(doChild(node, 1));
	}

	// *
	public Object visit(ASTTimes node, Object data) {
		return doChild(node, 0).mult(doChild(node, 1));
	}

	// /
	public Object visit(ASTDivide node, Object data) {
		return doChild(node, 0).div(doChild(node, 1));
	}

	// NOT
	public Object visit(ASTUnaryNot node, Object data) {
		return doChild(node, 0).not();
	}

	// + (unary)
	public Object visit(ASTUnaryPlus node, Object data) {
		return doChild(node, 0).unary_plus();
	}

	// - (unary)
	public Object visit(ASTUnaryMinus node, Object data) {
		return doChild(node, 0).unary_minus();
	}

	// Return string literal
	public Object visit(ASTCharacter node, Object data) {
		if (node.optimised == null)
			node.optimised = ValueString.stripDelimited(node.tokenValue);
		return node.optimised;
	}

	// Return integer literal
	public Object visit(ASTInteger node, Object data) {
		if (node.optimised == null)
			node.optimised = new ValueInteger(Long.parseLong(node.tokenValue));
		return node.optimised;
	}

	// Return floating point literal
	public Object visit(ASTRational node, Object data) {
		if (node.optimised == null)
			node.optimised = new ValueRational(Double.parseDouble(node.tokenValue));
		return node.optimised;
	}

	// Return true literal
	public Object visit(ASTTrue node, Object data) {
		if (node.optimised == null)
			node.optimised = new ValueBoolean(true);
		return node.optimised;
	}

	// Return false literal
	public Object visit(ASTFalse node, Object data) {
		if (node.optimised == null)
			node.optimised = new ValueBoolean(false);
		return node.optimised;
	}

	/**
	 * Quit expression.
	 * 
	 * @author amrwc
	 */
	public Object visit(ASTQuit node, Object data) {
		System.exit(0);
		return null;
	}
}
