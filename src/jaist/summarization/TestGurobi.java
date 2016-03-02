package jaist.summarization;

import gurobi.*;

/**
 * Created by chientran on 3/1/16.
 */
public class TestGurobi {
    public static void main(String[] args) throws GRBException{
        GRBEnv env = new GRBEnv("mip.log");
        GRBModel model = new GRBModel(env);

        GRBVar x = model.addVar(0.0, 1.0, 0.0, GRB.BINARY, "x");
        GRBVar y = model.addVar(0.0, 1.0, 0.0, GRB.BINARY, "y");
        GRBVar z = model.addVar(0.0, 1.0, 0.0, GRB.BINARY, "z");
        GRBVar m = model.addVar(0.0, 1.0, 0.0, GRB.BINARY, "m");
        GRBVar n = model.addVar(0.0, 1.0, 0.0, GRB.BINARY, "n");
        GRBVar t = model.addVar(0.0, 1.0, 0.0, GRB.BINARY, "t");

        GRBLinExpr expr = new GRBLinExpr();
        expr.addTerm(24, x);
        expr.addTerm(0, y);
        expr.addTerm(-24, z);
        expr.addTerm(18, m);
        expr.addTerm(30, n);
        expr.addTerm(-0, t);

        model.update();
        model.setObjective(expr, GRB.MAXIMIZE);

        model.optimize();

        System.out.println(x.get(GRB.DoubleAttr.X));
        System.out.println(y.get(GRB.DoubleAttr.X));
        System.out.println(z.get(GRB.DoubleAttr.X));
        System.out.println(m.get(GRB.DoubleAttr.X));
        System.out.println(n.get(GRB.DoubleAttr.X));
        System.out.println(t.get(GRB.DoubleAttr.X));
    }
}
