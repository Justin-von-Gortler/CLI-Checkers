import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.StringTokenizer;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Executes a two player game of checkers through an interactive cli
 */
class Main {
    public static void main(String args[]) throws IOException {
        System.out.println("Welcome! Starting a new game of Checkers.");
        Game game = new Game();
        game.printBoard();
        System.out.println("Which team will start? Enter Red or Black:");
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        StringTokenizer st = new StringTokenizer(br.readLine());
        Team nextTeam = Team.valueOf(st.nextToken().toUpperCase());
        Team previousTeam = nextTeam == Team.BLACK ? Team.RED : Team.BLACK;
        while (!game.isComplete) {
            st = newTurn(nextTeam, game, br);
            boolean success = false;
            while (!success) {
                success = executeTurn(game, st);
                if (success) {
                    Team temp = previousTeam;
                    previousTeam = nextTeam;
                    nextTeam = temp;
                } else {
                    System.out.println("The move provided could not be executed. Please provide a valid move.");
                    st = promptForMove(br);
                }
            }
        }
        game.printBoard();
        System.out.println("Congratulations " + previousTeam + " team, you won!");
        br.close();
    }

    /**
     * Prints instructions prompting the user for the move input
     * 
     * @param br the bufferred reader recieving the users input
     * @return the input provided by the user
     * @throws IOException
     */
    private static StringTokenizer promptForMove(BufferedReader br) throws IOException {
        System.out.println(
                "Please select your move. Enter the move or capture (m or c), the location of the piece you would like to move, and the location you would like to place that piece:");
        System.out.println("Example: m 0 2 1 3");
        System.out.println(
                "If you want to attempt multiple sequential captures, you may add subsequent locations to jump too in the exact order you wish them to execute.");
        System.out.println("Example: c 0 2 2 4 4 6");
        return new StringTokenizer(br.readLine());
    }

    /**
     * Initiates a new turn in the game
     * 
     * @param nextTeam the team executing the turn
     * @param game     the current game
     * @param br       the bufferred reader capturing the user input
     * @return the input provided by the user
     * @throws IOException
     */
    private static StringTokenizer newTurn(Team nextTeam, Game game, BufferedReader br) throws IOException {
        game.printBoard();
        System.out.println("Gathering are all possible moves for the " + nextTeam + " team.");
        Map<Checker, List<Location>> validMoves = getValidMovesForTeam(game, false, nextTeam);
        System.out.println("Here are all the possible moves for the " + nextTeam + " team:");
        printMoves(validMoves);
        Map<Checker, List<Location>> validCaptures = getValidMovesForTeam(game, true, nextTeam);
        System.out.println("Here are all the possible captures for the " + nextTeam + " team:");
        printMoves(validCaptures);
        return promptForMove(br);
    }

    /**
     * Executes a turn based on input from the user
     * 
     * @param game the current game
     * @param st   the user input
     * @return set true if the turn was executed successfully
     */
    private static boolean executeTurn(Game game, StringTokenizer st) {
        boolean success = false;
        try {
            String moveType = st.nextToken(" ").toLowerCase();
            if (moveType.equals("s")) {
                System.out.println("Player has chosed to skip turn.");
                return true;
            }
            boolean isCapture = moveType.equals("c");
            Location start = new Location(Integer.parseInt(st.nextToken(" ")), Integer.parseInt(st.nextToken(" ")));
            Optional<Checker> toMove = game.getCheckerByLocation(start);
            if (toMove.isEmpty()) {
                System.out.println("Please select a valid start location.");
            } else {
                Location end = new Location(Integer.parseInt(st.nextToken(" ")), Integer.parseInt(st.nextToken(" ")));
                if (isCapture) {
                    List<Location> captures = new ArrayList<>();
                    captures.add(end);
                    while (st.hasMoreTokens()) {
                        captures.add(
                                new Location(Integer.parseInt(st.nextToken(" ")), Integer.parseInt(st.nextToken(" "))));
                    }
                    success = capture(game, toMove.get(), captures);
                } else {
                    success = move(game, toMove.get(), end);
                }
            }
        } catch (RuntimeException e) {
            System.out.println("The provided input is not in the correct format.");
        }
        return success;
    }

    /**
     * Print a series of moves
     * 
     * @param moves the checker mapped to it's corresponding available moves
     */
    private static void printMoves(Map<Checker, List<Location>> moves) {
        for (Map.Entry<Checker, List<Location>> entry : moves.entrySet()) {
            System.out.print("(" + entry.getKey().location + ")");
            System.out.print(" -> ");
            for (Location move : entry.getValue()) {
                System.out.print("(" + move + "), ");
            }
            System.out.println();
        }
    }

    /**
     * Executes one or more captures for a given piece provided each capture is
     * valid. Multiple captures can be completed in succession,
     * provided the previous capture succeeded.
     * 
     * @param game     the current game
     * @param checker  the checker piece to use for capturing
     * @param captures a list of locations to move to in completion of a capture to
     *                 attempt provided in order
     * @return set true if at least one capture was completed successfully
     */
    private static boolean capture(Game game, Checker checker, List<Location> captures) {
        boolean success = false;
        // Multiple captures can occur in succession, provided the previous capture was
        // successful
        for (Location capture : captures) {
            if (game.isComplete) {
                break;
            }
            Map<Checker, List<Location>> validCaptures = getValidMovesForTeam(game, true, checker.team);
            if (validCaptures.get(checker).contains(capture)) {
                Location checkerToCapture = getLocationOfCheckerToCapture(checker.location, capture);
                Optional<Checker> toRemove = game.checkers.stream()
                        .filter(c -> (c.location.equals(checkerToCapture)))
                        .findAny();
                if (toRemove.isPresent()) {
                    game.checkers.remove(toRemove.get());
                    checker.location = capture;
                    if (!checker.isKing) {
                        checker.isKing = shouldBeKing(checker, game.boardSize);
                    }
                    if (!(game.checkers.stream()
                            .filter(currentChecker -> currentChecker.team != checker.team)
                            .findAny()
                            .isPresent())) {
                        game.isComplete = true;
                    }
                    success = true;
                }
            } else {
                // If the capture was unsuccessful, do not attempt any further captures
                break;
            }
        }
        return success;
    }

    /**
     * Executes a move for a given piece provided the move is valid. If the checker
     * reaches the opposite side of the board, the checker
     * becomes a king.
     * 
     * @param game    the current game
     * @param checker the checker piece to use for capturing
     * @param move    the location to attempt to move to
     * @return set true if move successfully executed
     */
    private static boolean move(Game game, Checker checker, Location move) {
        Map<Checker, List<Location>> validMoves = getValidMovesForTeam(game, false, checker.team);
        if (validMoves.get(checker).contains(move)) {
            checker.location = move;
            if (!checker.isKing) {
                checker.isKing = shouldBeKing(checker, game.boardSize);
            }
            return true;
        }
        return false;
    }

    /**
     * Gets the location of the checker that is attempting to be captured. This
     * should always be the location in between
     * the start and end of the move.
     * 
     * @param start where the move begins
     * @param end   where the move ends
     * @return the location of the checker that would be removed
     */
    private static Location getLocationOfCheckerToCapture(Location start, Location end) {
        int xMiddle = (start.x + end.x) / 2;
        int yMiddle = (start.y + end.y) / 2;
        return new Location(xMiddle, yMiddle);
    }

    /**
     * Checks whether or not a checker should become king
     * 
     * @param checker   the checker to inspect
     * @param boardSize the size of the board
     * @return true if the checker should become a king
     */
    private static boolean shouldBeKing(Checker checker, int boardSize) {
        if (checker.team == Team.RED) {
            if (checker.location.y == 0) {
                return true;
            }
        } else {
            if (checker.location.y == boardSize - 1) {
                return true;
            }
        }
        return false;
    }

    /**
     * Finds all the potential valid moves or captures for every piece for the given
     * game
     * 
     * @param game      the current game
     * @param isCapture set true when searching for a valid captures
     * @return a mapping of each checker and the list of valid moves or captures the
     *         checker could execute for the given game
     */
    private static Map<Checker, List<Location>> getAllValidMoves(Game game, boolean isCapture) {
        Map<Location, Checker> locationToCheckerMap = game.getLocationToCheckerMapping();
        Map<Checker, List<Location>> validMovesPerChecker = new HashMap<>();
        for (Checker checker : game.checkers) {
            List<Location> allPossibleMoves = getAllPossibleMoves(checker, isCapture);
            List<Location> validMoves = new ArrayList<>();
            for (Location move : allPossibleMoves) {
                if (isCapture) {
                    if (isValidCapture(game.boardSize, checker, move, locationToCheckerMap)) {
                        validMoves.add(move);
                    }
                } else {
                    if (isValidMove(game.boardSize, checker, move, new ArrayList<>(locationToCheckerMap.keySet()))) {
                        validMoves.add(move);
                    }
                }
            }
            validMovesPerChecker.put(checker, validMoves);
        }
        return validMovesPerChecker;
    }

    /**
     * Finds all the potential valid moves or captures for every piece for the given
     * game and team
     * 
     * @param game      the current game
     * @param isCapture set true when searching for a valid captures
     * @param team      the team to query moves for
     * @return a mapping of each checker and the list of valid moves or captures the
     *         checker could execute for the given game
     */
    private static Map<Checker, List<Location>> getValidMovesForTeam(Game game, boolean isCapture, Team team) {
        Map<Location, Checker> locationToCheckerMap = game.getLocationToCheckerMapping();
        Map<Checker, List<Location>> validMovesPerChecker = new HashMap<>();
        List<Checker> checkersForTeam = game.checkers.stream().filter(checker -> (checker.team == team))
                .collect(Collectors.toList());
        for (Checker checker : checkersForTeam) {
            List<Location> allPossibleMoves = getAllPossibleMoves(checker, isCapture);
            List<Location> validMoves = new ArrayList<>();
            for (Location move : allPossibleMoves) {
                if (isCapture) {
                    if (isValidCapture(game.boardSize, checker, move, locationToCheckerMap)) {
                        validMoves.add(move);
                    }
                } else {
                    if (isValidMove(game.boardSize, checker, move, new ArrayList<>(locationToCheckerMap.keySet()))) {
                        validMoves.add(move);
                    }
                }
            }
            validMovesPerChecker.put(checker, validMoves);
        }
        return validMovesPerChecker;
    }

    /**
     * Finds every possible move or captures a piece could make for the given game
     * 
     * @param checker   the current checker
     * @param isCapture set true when searching for a valid captures
     * @return a list of all moves or captures the checker could execute for the
     *         given game
     */
    private static List<Location> getAllPossibleMoves(Checker checker, boolean isCapture) {
        Location currentLocation = checker.location;
        List<Location> possibleLocations = new ArrayList<>();

        // Captures will leap frog the checker they are capturing during a capture,
        // effectively moving two diagonals
        int offset = isCapture ? 2 : 1;
        Location upRight = new Location(currentLocation.x + offset, currentLocation.y - offset);
        Location upLeft = new Location(currentLocation.x - offset, currentLocation.y - offset);
        Location downRight = new Location(currentLocation.x + offset, currentLocation.y + offset);
        Location downLeft = new Location(currentLocation.x - offset, currentLocation.y + offset);

        // Kings are able to move up and down the board
        if (checker.isKing) {
            possibleLocations.add(upRight);
            possibleLocations.add(upLeft);
            possibleLocations.add(downRight);
            possibleLocations.add(downLeft);
        } else if (checker.team == Team.RED) {
            possibleLocations.add(upRight);
            possibleLocations.add(upLeft);
        } else {
            possibleLocations.add(downRight);
            possibleLocations.add(downLeft);
        }
        return possibleLocations;
    }

    /**
     * Validates that given move is valid for the dimensions of the game board
     * 
     * @param boardSize    the size of the board, providing dimensions for possible
     *                     checker locations
     * @param proposedMove the move that is being attempted
     * @return true if the move location is within the board dimensions
     */
    private static boolean isValidMoveForBoardDimensions(int boardSize, Location proposedMove) {
        boolean isValidXCoordinate = proposedMove.x >= 0 && proposedMove.x < boardSize;
        boolean isValidYCoordinate = proposedMove.y >= 0 && proposedMove.y < boardSize;
        return isValidXCoordinate && isValidYCoordinate;
    }

    /**
     * Checks if the proposed move is valid. A move is considered valid if it is
     * within the dimensions the game board,
     * and is not attempting to move to a space that is occupied by a checker.
     * 
     * @param boardSize           the size of the board, providing dimensions for
     *                            possible checker locations
     * @param currentChecker      the checker to validate the move against
     * @param proposedMove        the move that is being attempted
     * @param allCheckerLocations the locations of every checker on the board
     * @return true if the move is valid
     */
    private static boolean isValidMove(int boardSize, Checker currentChecker, Location proposedMove,
            List<Location> allCheckerLocations) {
        return isValidMoveForBoardDimensions(boardSize, proposedMove) && !allCheckerLocations.contains(proposedMove);
    }

    /**
     * Checks if the proposed capture is valid. A capture is considered valid if it
     * is a valid move, and there is a checker
     * of the opposing team in the space between where the capturing checker started
     * and ended the move.
     * 
     * @param boardSize            the size of the board, providing dimensions for
     *                             possible checker locations
     * @param currentChecker       the checker to validate the move against
     * @param proposedCapture      the capture that is being attempted
     * @param locationToCheckerMap a mapping of the locations occupied by checkers,
     *                             and their corresponding checker
     * @return true if the move is valid
     */
    private static boolean isValidCapture(int boardSize, Checker currentChecker, Location proposedCapture,
            Map<Location, Checker> locationToCheckerMap) {
        Location toCapture = getLocationOfCheckerToCapture(currentChecker.location, proposedCapture);
        boolean hasCheckerToCapture = locationToCheckerMap.containsKey(toCapture) &&
                !(locationToCheckerMap.get(toCapture).team == currentChecker.team);
        return isValidMove(boardSize, currentChecker, proposedCapture, new ArrayList<>(locationToCheckerMap.keySet()))
                && hasCheckerToCapture;
    }

    /**
     * A set of x & y coordinates corresponding to a position on the game board
     */
    public static class Location {
        /*
         * The position along the x axis
         */
        public int x;

        /*
         * The position along the y axis
         */
        public int y;

        public Location(int x, int y) {
            this.x = x;
            this.y = y;
        }

        @Override
        public boolean equals(Object obj) {
            Location otherLocation = (Location) obj;
            return this.x == otherLocation.x && this.y == otherLocation.y;
        }

        @Override
        public int hashCode() {
            return (this.x * 10) + this.y;
        }

        @Override
        public String toString() {
            return this.x + ", " + this.y;
        }
    }

    /**
     * An individual game piece used in a game of checkers
     */
    public static class Checker {
        /*
         * The current position of the checker in the game
         */
        public Location location;

        /*
         * The team the checker belongs too
         */
        public Team team;

        /*
         * Whether or not this piece has been kinged. Kings have the unique ability to
         * move up and down the game board, regardless of team
         */
        public boolean isKing;

        public Checker(Location location, Team team) {
            this.location = location;
            this.team = team;
            this.isKing = false;
        }

        @Override
        public boolean equals(Object obj) {
            Checker otherChecker = (Checker) obj;
            return this.location.equals(otherChecker.location) && this.team == otherChecker.team;
        }

        @Override
        public int hashCode() {
            return this.location.hashCode() + this.team.hashCode();
        }
    }

    /*
     * The team a checker belongs too. Checkers on the red team move up the board,
     * checkers on the black team move down the board
     */
    public static enum Team {
        RED,
        BLACK
    }

    /*
     * Models a single game of checkers
     */
    public static class Game {
        /*
         * All the checkers for the current game
         */
        public List<Checker> checkers;

        /*
         * The length and width of the board. Helps understand the scope of valid
         * checker locations
         */
        public int boardSize;

        /*
         * Whether or not the game is completed. Set true when there are no remaining
         * checkers for a given team
         */
        public boolean isComplete;

        public Game(int boardSize) {
            this.boardSize = boardSize;
            this.checkers = new ArrayList<>();
            ;
            this.isComplete = false;
        }

        /**
         * Provides a game of standard size and setup for checkers
         */
        public Game() {
            this.boardSize = 8;
            List<Checker> checkers = new ArrayList<>();
            for (int i = 0; i < this.boardSize; i++) {
                if (i > 2 && i < 5) {
                    continue;
                }
                for (int j = i % 2; j < this.boardSize; j += 2) {
                    Location location = new Location(j, i);
                    if (i <= 2) {
                        checkers.add(new Checker(location, Team.BLACK));
                    } else {
                        checkers.add(new Checker(location, Team.RED));
                    }
                }
            }
            this.checkers = checkers;
            this.isComplete = false;
        }

        public Optional<Checker> getCheckerByLocation(Location checkerLocation) {
            for (Checker checker : this.checkers) {
                if (checker.location.equals(checkerLocation)) {
                    return Optional.of(checker);
                }
            }
            return Optional.empty();
        }

        public void printBoard() {
            Map<Location, Checker> locationToCheckerMap = this.getLocationToCheckerMapping();
            for (int i = 0; i < this.boardSize; i++) {
                for (int j = 0; j < this.boardSize; j++) {
                    Location location = new Location(j, i);
                    System.out.print("|");
                    if (locationToCheckerMap.containsKey(location)) {
                        Checker checker = locationToCheckerMap.get(location);
                        String toPrint;
                        if (checker.team == Team.RED) {
                            toPrint = "r";
                        } else {
                            toPrint = "b";
                        }
                        if (checker.isKing) {
                            toPrint = toPrint.toUpperCase();
                        }
                        System.out.print(toPrint);
                    } else {
                        System.out.print(" ");
                    }
                    System.out.print("|");
                }
                System.out.println();
            }
        }

        /*
         * Maps all of the locations containing a checker on the game board to their
         * corresponding checker
         */
        public Map<Location, Checker> getLocationToCheckerMapping() {
            Map<Location, Checker> locationToCheckerMap = new HashMap<>();
            for (Checker checker : this.checkers) {
                locationToCheckerMap.put(checker.location, checker);
            }
            return locationToCheckerMap;
        }
    }
}
