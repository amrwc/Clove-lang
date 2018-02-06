package uk.ac.derby.ldi.sili.vm.instructions;

import uk.ac.derby.ldi.sili.values.Value;
import uk.ac.derby.ldi.sili.vm.Context;
import uk.ac.derby.ldi.sili.vm.Instruction;

public class OpGte extends Instruction {
	public final void execute(Context context) {
		Value v = context.pop();
		context.push(context.pop().gte(v));
	}
}
