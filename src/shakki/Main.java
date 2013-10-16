/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package shakki;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import ourevaluator.OurEvaluator;
import position.Evaluator;
import position.Position;

public class Main {

    static Evaluator ce; // current evaluator
    static Evaluator oe; // our evaluator
    static Evaluator ye; // your evaluator

    static class PositionComparator implements Comparator<Position> {

        Map<Position, Double> evaluations = new HashMap<Position, Double>();

        PositionComparator(List<Position> positions) {
            for (Position p : positions) {
                evaluations.put(p, ce.eval(p));
            }
        }

        @Override
        public int compare(Position p1, Position p2) {
            return Double.compare(evaluations.get(p1), evaluations.get(p2));
        }
    }

    static double alphabeta(Position p, int depth, double alpha, double beta, int player) {
        // 0 tries to maximize, 1 tries to minimize
        if (p.winner == -1) {
            return -1E10 - depth; // prefer to win sooner
        }
        if (p.winner == +1) {
            return +1E10 + depth; // and lose later
        }
        if (depth == 0) {
            return ce.eval(p);
        }
        Vector<Position> P = p.getNextPositions();
        Collections.sort(P, new PositionComparator(P));
        if (player == 0) {
            Collections.reverse(P);
        }

        //System.out.println("Pelaaja: "+player+" koko: "+P.size());
        //System.out.println("EnsimmÃ¤inen: "+eval(P.elementAt(0)));
        //System.out.println("Viimeinen: "+eval(P.lastElement()));
        if (player == 0) {
            for (int i = 0; i < P.size(); ++i) {
                alpha = Math.max(alpha, alphabeta(P.elementAt(i), depth - 1, alpha, beta, 1));
                if (beta <= alpha) {
                    break;
                }
            }
            return alpha;
        }

        for (int i = 0; i < P.size(); ++i) {
            beta = Math.min(beta, alphabeta(P.elementAt(i), depth - 1, alpha, beta, 0));
            if (beta <= alpha) {
                break;
            }
        }

        return beta;
    }

    static double minmax(Position p, int depth, int player) {
        double alpha = -Double.MAX_VALUE, beta = Double.MAX_VALUE;
        return alphabeta(p, depth, alpha, beta, player);
        /*
         //return (p.whiteToMove ? -1 : 1) * eval(p);
         if(depth <= 0) return eval(p);
        
         double val = eval(p);
         if(Math.abs(val) > 1e8) return val; // prevent king exchange :/
        
         double alpha = p.whiteToMove ? 1e12 : -1e12;
        
         Vector<Position> P = p.getNextPositions();
        
         for(int i = 0; i < P.size(); ++i) {
         if(p.whiteToMove) {
         alpha = Math.min(alpha, minmax(P.elementAt(i),depth-1));
         } else alpha = Math.max(alpha, minmax(P.elementAt(i),depth-1));
         }
        
         return alpha;
         */
    }

    static class MinMax implements Callable<Double> {

        private final Position position;
        private final int depth;
        private final int player;

        public MinMax(Position position, int depth, int player) {
            this.position = position;
            this.depth = depth;
            this.player = player;
        }

        @Override
        public Double call() throws Exception {
            return minmax(position, depth, player);
        }
    }

    public static void main(String[] args) {
        // you get the white pieces, we play the black pieces
        oe = new OurEvaluator();
        ye = new YourEvaluator();
        //ye = new OurEvaluator();
        int depth = 5;
        Position p = new Position();
        p.setStartingPosition();

        long ms = System.currentTimeMillis();
        int maxMoves = 200;
        int move = 0;

        int cpuCount = Runtime.getRuntime().availableProcessors();
        ExecutorService executor = Executors.newFixedThreadPool(cpuCount);
        System.out.println("using " + cpuCount + " threads");

        try {
            while (true) {
                Vector<Position> P = p.getNextPositions();

                if (p.winner == +1) {
                    System.out.println("White won.");
                    break;
                }

                if (p.winner == -1) {
                    System.out.println("Black won.");
                    break;
                }

                if (P.isEmpty()) {
                    System.out.println("No more available moves");
                    break;
                }

                if (move == maxMoves) {
                    System.out.println("Maximum number of moves used");
                    break;
                }

                Position bestPosition = new Position();

                List<Future<Double>> values = new ArrayList<Future<Double>>();
                for (int i = 0; i < P.size(); ++i) {
                    FutureTask<Double> task = new FutureTask<Double>(
                            new MinMax(P.elementAt(i), depth, p.whiteToMove ? 1 : 0)
                    );
                    values.add(task);
                    executor.execute(task);
                }

                if (p.whiteToMove) {
                    ce = ye;
                    double max = -1. / 0.;

                    for (int i = 0; i < P.size(); ++i) {
                        double val = values.get(i).get();
                        if (max < val) {
                            bestPosition = P.elementAt(i);
                            max = val;
                        }
                    }

                } else {
                    ce = oe;
                    double min = 1. / 0.;
                    for (int i = 0; i < P.size(); ++i) {
                        double val = values.get(i).get();
                        if (min > val) {
                            bestPosition = P.elementAt(i);
                            min = val;
                        }
                    }
                }

                assert p.whiteToMove != bestPosition.whiteToMove;
                p = bestPosition;

                long curtime = System.currentTimeMillis();
                System.out.println("Move " + ++move + " took " + ((curtime - ms) / 1000.0) + " seconds");
                ms = curtime;
            }
        } catch (InterruptedException e) {
            System.out.println("Interrupted");
        } catch (ExecutionException e) {
            System.out.println("Exception in algorithm");
        }
        executor.shutdown();

        p.print();

        /*
         for(int i = 0; i < 60; ++i) {
         System.out.println("----------------");
         Vector<Position> P = p.getNextPositions();
         p = P.elementAt((int)(Math.random()*P.size()));
         p.print();
         System.out.println("Eval: "+eval(p));
         }
         */
    }
}
