package apptest.verification;

import java.util.ArrayList;
import java.util.List;
import org.assertj.core.groups.Tuple;

public class Tuples extends ArrayList<Tuple> {

	private static final long serialVersionUID = -4900652313152584516L;

	public static Tuples create() {
		return new Tuples();
	}

	public Tuples with(Tuple t) {
		super.add(t);
		return this;
	}

	public Tuples with(List<Tuple> l) {
		super.addAll(l);
		return this;
	}
}
