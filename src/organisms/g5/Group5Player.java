package organisms.g5;

import organisms.Move;
import organisms.ui.OrganismsGame;
import organisms.OrganismsPlayer;

import java.awt.Color;
import java.util.Optional;
import java.util.Random;

public class Group5Player implements OrganismsPlayer {

    enum OCCUPANT {
        empty,              // i.e no foreign object/organism on this square
        other_organism,     // can be of the same or a different species
        food
    }

    private OrganismsGame game;
    private int currentFoodHere = 0;
    private int generationCount = 0;
    private Random random = new Random();
    private int externalState = 0;
    private int stepsInDirection = 0;
    private int stepsWithoutFood = 0;

    @Override
    public void register(OrganismsGame game, int dna) throws Exception {
        this.game = game;
    }

    @Override
    public String name() {
        return "Group 5 Player";
    }

    @Override
    public Color color() {
        return new Color(200, 200, 200, 255);
    }

    @Override
    public Move move(int foodHere, int energyLeft, boolean foodN, boolean foodE, boolean foodS, boolean foodW,
                     int neighborN, int neighborE, int neighborS, int neighborW) throws Exception {

        // force reproduction at high energy
        if (energyLeft >= 499) {
            int actionIndex = this.random.nextInt(Action.getNumActions());
            Action actionChoice = Action.fromInt(actionIndex);

            return Move.reproduce(actionChoice, actionIndex);
        }

        // If there is food, stay put until all food is consumed
        // resets the counter of how many steps have been traveled without food
        if (foodHere > 0) {
            currentFoodHere = foodHere;
            stepsWithoutFood = 0; // Reset steps without food
            System.out.println("Action " + Action.STAY_PUT.toString() + ", " + foodHere + " of food remaining on this square.");
            return Move.movement(Action.STAY_PUT);

        } else if (currentFoodHere > 0) {
            currentFoodHere = foodHere; // update the remaining food
            System.out.println("Action " + Action.STAY_PUT.toString() + ", " + foodHere + " of food remaining on this square.");

            return Move.movement(Action.STAY_PUT);
        }

        // If external state is not 0, move in the direction indicated by external state
        // increment counter for movement
        if (externalState != 0) {
            stepsInDirection++;
            Action moveAction = switch (externalState) {
                case 1 -> Action.NORTH;
                case 2 -> Action.EAST;
                case 3 -> Action.SOUTH;
                case 4 -> Action.WEST;
                default -> Action.STAY_PUT;
            };

            System.out.println("Action " + moveAction.toString() + " , moving in the direction of external state.");

            // resets externalState which is used when a child organism is born
            if (stepsInDirection >= 2) {
                externalState = 0;
                stepsInDirection = 0;
            }

            // not still just 'given birth', continue moving in the direction it was given birth
            return Move.movement(moveAction);
        }

        // Check if there's food nearby and move towards it
        if (foodN) {
            System.out.println("Action NORTH, moving towards food.");
            return Move.movement(Action.NORTH);
        } else if (foodE) {
            System.out.println("Action EAST, moving towards food.");
            return Move.movement(Action.EAST);
        } else if (foodS) {
            System.out.println("Action SOUTH, moving towards food.");
            return Move.movement(Action.SOUTH);
        } else if (foodW) {
            System.out.println("Action WEST, moving towards food.");
            return Move.movement(Action.WEST);
        }

        // Increase steps without food
        stepsWithoutFood++;

        // If no food is found after 9 steps, start splitting for 2 generations
        if (stepsWithoutFood >= 9) {
            stepsWithoutFood = 0; // Reset steps without food

            // if the current organism is not a grandpa
            if (generationCount < 2) {
                generationCount++;

                System.out.println("Action REPRODUCE, generation count: " + generationCount);
                int actionIndex = this.random.nextInt(Action.getNumActions());
                Action actionChoice = Action.fromInt(actionIndex);

                externalState = actionChoice.ordinal() + 1; // Set the direction of reproduction as the external state
                return Move.reproduce(actionChoice, actionIndex);
            } else {
                generationCount = 0;
            }
        }

        // Calculate biases param
        double foodBiase = 15.0;
        double energyBiase = 8.0;
        double repoBiase = 5.0;
        double[] biases = applyBias(foodBiase, energyBiase, repoBiase);

        // Optimize move based on netBenefit
        double maxBenefit = Double.NEGATIVE_INFINITY;
        Action bestMove = Action.STAY_PUT;

        // generate occupant based on current organism condition
        for (Action action : Action.values()) {
            if (action == Action.STAY_PUT && energyLeft > 50) continue; // Exclude STAY_PUT if energy is high
            boolean move = (action != Action.STAY_PUT);

            OCCUPANT occupant = (action == Action.STAY_PUT) ? OCCUPANT.empty :
                    (foodN && action == Action.NORTH) ||
                            (foodE && action == Action.EAST) ||
                            (foodS && action == Action.SOUTH) ||
                            (foodW && action == Action.WEST) ? OCCUPANT.food : OCCUPANT.empty;

            // generate maximum benefit move
            double benefit = netBenefit(move, occupant, false, Optional.of(biases), energyLeft);
            if (benefit > maxBenefit) {
                maxBenefit = benefit;
                bestMove = action;
            }
        }

        System.out.println("Action " + bestMove.toString() + " , chosen based on net benefit.");
        return Move.movement(bestMove);
    }

    protected double netBenefit(boolean move, OCCUPANT occupant, boolean reproduce, Optional<double[]> override, int energyLeft) {

        // get game costings (default values)
        double v1 = game.v();              // move
        double v2 = game.v();              // reproduce
        double s = game.s();
        double u = game.u();

        // give player opportunity to override these
        if (override.isPresent() && override.get().length == 4) {
            v1 = override.get()[0];
            v2 = override.get()[1];
            s = override.get()[2];
            u = override.get()[3];
        }

        // calculate override of specific moves
        if (reproduce) {
            if (move) throw new IllegalArgumentException("Bad argument! Conflicting values for reproduce and move.");
            if (occupant.equals(OCCUPANT.other_organism)) throw new IllegalArgumentException("Bad argument! Conflicting values for reproduce and occupant.");
            return -v2;
        }

        if (!move) {
            if (occupant.equals(OCCUPANT.other_organism)) throw new IllegalArgumentException("Bad argument! Conflicting values for move and occupant.");
            double penalty = (energyLeft > 300) ? 50 : 0; // Apply penalty if energy is high
            if (occupant.equals(OCCUPANT.food)) return u - s - penalty; // gain from food (eat) - cost of staying (x) - penalty
            else return -s - penalty;
        } else {
            if (occupant.equals(OCCUPANT.food)) return u - v1;              // gain from food (eat) - cost of movement to get that food (exert)
            if (occupant.equals(OCCUPANT.empty)) return -v1;
            if (occupant.equals(OCCUPANT.other_organism)) {
                System.err.println("This move has poor efficiency! Please consider remain.");
                return -game.v();
            }
        }

        // we should never get here
        throw new IllegalArgumentException("No valid strategy for this combination of arguments. Please try again.");
    }

    protected double[] applyBias(double foodW, double energyW, double repoW) {

        // check inputs
        if (foodW < 0 || energyW < 0 || repoW < 0 || foodW > 10 || energyW > 10 || repoW > 10)
            throw new IllegalArgumentException("Bad input! Priority values must be between 0 and 10, inclusive.");

        // container for return value
        double[] netBenefits = new double[4];            // order of variables is V1, V2, S, U

        // work out priorities - expressed as a proportion of the max (30)
        double fp = (foodW / 30);
        double ep = (energyW / 30);
        double rp = (repoW / 30);

        // work out deltas for each game-set variable
        double v = 20 - 2;
        double u = 500 - 10;

        // change net benefits to reflect new biases
        netBenefits[0] = game.v();                     // leave cost of movement the same (never changed by weightings)
        netBenefits[1] = (game.v() - (v * rp));        // reduce cost of reproduction by scaling factor rp
        netBenefits[2] = (game.s() - (1 * ep));        // reduce cost of staying in place by scaling factor ep
        netBenefits[3] = (game.u() + (u * fp));        // increase the benefit of food by scaling factor fp

        // return new set of net benefits [order: v1, v2, s, u] with biases applied
        return netBenefits;
    }

    @Override
    public int externalState() throws Exception {
        return externalState;
    }
}
