package control;

import control.action.*;
import entity.Board;
import entity.Player;
import entity.card.Card;
import entity.dice.Dice;
import entity.dice.DiceResult;
import entity.dice.SpeedDieResult;
import entity.property.Building;
import entity.property.Dorm;
import entity.property.Facility;
import entity.property.Property;
import entity.tile.*;
import gui.GameScreenController;
// import javafx.beans.property.Property;

// how does ui and this communicate?
// UI and UIController class
// UIControl class will have both UI and MonopolyGame
// It will call appropriate functions according to the user input
// how do we bankrupt the player?

// about multiplayer
// return ArrayList<Action> to server
// server will send these actions to other clients
// clients will process these actions

import java.io.IOException;
import java.util.ArrayList;
import java.util.Random;

public class MonopolyGame {

    Board board;
    int turn;
    public static ActionLog actionLog;
    PlayerController playerController;
    GameMode mode;
//    boolean speedDieMode;
//    boolean alliance;
    boolean gameStarted = false;
    boolean gamePaused = false;
    int doubleCount = 0;
    int moveCount = 0; // sum of dices
    Dice dice;
    DiceResult diceResult;
    GameScreenController ui;


    public MonopolyGame(ArrayList<Player> players, GameScreenController ui, GameMode mode) throws IOException {
        board = new Board();
        turn = 0;
        actionLog = ActionLog.getInstance();
        playerController = new PlayerController(players);
        dice = new Dice(System.currentTimeMillis());
        this.ui = ui;
        this.mode = mode; // set the game mode accordingly
    }

    public MonopolyGame() throws IOException {
        board = new Board();
        turn = 0;
        actionLog = ActionLog.getInstance();
        //playerController = new PlayerController(players);
        dice = new Dice(System.currentTimeMillis());
        //this.ui = ui;
    }


    public MonopolyGame(ArrayList<Player> players, long seed, boolean speedDieMode) throws IOException {
        board = new Board();
        turn = 0;
        actionLog = ActionLog.getInstance();
        playerController = new PlayerController(players);
        dice = new Dice(seed);
        this.mode = mode; // set the game mode accordingly
    }

    public void addPlayer(Player player) {
        playerController.addPlayer(player);
    }

    public void stopGame() {
        gameStarted = false;
    }

    public Player startGame() {
        ArrayList<Player> players = playerController.getPlayers();
        System.out.println("Size of players: " + players.size());
        playerController.setActivePlayer(players.get(new Random().nextInt(players.size()))); // randomize the process, it creates int with size, how??
        gameStarted = true;

        return playerController.getActivePlayer();
    }

    public void rollDice() {
        diceResult = dice.roll(mode.isSpeedDie());
        //diceResult = dice.roll(false);

        if ( diceResult.isDouble() ) {
            doubleCount++;
        }

        Player player = getActivePlayer();
        Action action = new RollDiceAction(player, diceResult);
        action.act();
        ui.sendAction(action); // this line causes disconnection, why??
        ui.sendObject(diceResult);

        moveCount = computeMoveCount();
        //moveCount = diceResult.getFirstDieResult() + diceResult.getSecondDieResult();
        //return diceResult; // return because ui will show this result
    }

    public void processTurn() {
        Player player = playerController.getActivePlayer();

        if (doubleCount == 3) { // if this is the third double, put player into the jail
            GoToJailAction action = new GoToJailAction(player.getPlayerId());
            action.act();
            ui.sendAction(action);
            doubleCount = 0;
        }
        else if ((diceResult.isDouble() && player.isInJail()) || (player.getJailTurnCount() == 2)) {
            GetOutOfJailAction action = new GetOutOfJailAction(player.getPlayerId());
            action.act();
            ui.sendAction(action);
            player.setJailTurnCount(0);
        }
            if (!player.isInJail()) {
                if (moveCount > 0) {
                    // all normal cases for diceResult will result in with a normal move
                    MoveAction action = new MoveAction(player.getPlayerId(), moveCount); // try catch? PlayerIsInJailException
                    action.act();
                    ui.sendAction(action);
                }
                else if (moveCount == 0) {
                    //ToDo
                    // implement the edge case in Mr Monopoly, where player couldn't move
                    // as there were no tile to pay rent,
                    // so don't process the tile they are currently landing again
                    // etc. don't draw a card, pay tax etc.
                    //ToDo
                    // currently whoever rolled mr monopoly won't use the white dice
                    // but whole MR. Monopoly concept involves resolving the tile
                    // before ever using Mr. monopoly
                    // etc. player can't use Mr. Monopoly if they are in jail
                }
                else if (moveCount == - 1) {
                    //TODO
                    // implement the BUS behaviour
                    // where we will ask user to select one of the dices or both
                }
                else if (moveCount == -2) {
                    //TODO
                    // ask user to select a tile from the board
                    // and get the position
                    //FreeMoveAction action = new FreeMoveAction(player.getPlayerId(), position)
                    //action.act();
                    //ui.sendAction(action);
                }

                ui.updateBoardState();

                Tile tile = board.getTiles().get(player.getPosition());

                if (tile instanceof PropertyTile) {
                    processPropertyTile((PropertyTile) board.getTiles().get(player.getPosition()));
                }
                else if (tile instanceof JailTile || tile instanceof FreeParkingTile) {
                    // nothing, skip the turn
                }
                else if (tile instanceof StartTile) {
                    // new PassAction(player).act();
                }
                else if (tile instanceof TaxTile) {
                    TaxTile taxTile = (TaxTile) tile;
                    RemoveMoneyAction action2 = new RemoveMoneyAction(player.getPlayerId(), taxTile.getAmount());
                    action2.act();
                    ui.sendAction(action2);
                }
                else if (tile instanceof GoToJailTile) {
                    GoToJailAction action2 = new GoToJailAction(player.getPlayerId());
                    action2.act();
                    ui.sendAction(action2);
                }
                else if (tile instanceof CardTile) {
                    CardTile cardTile = (CardTile) tile;
                    if (cardTile.getCardType() == CardTile.CardType.CHANCE_CARD)
                        processChanceCardTile();
                    else
                        processCommunityChestCardTile();
                }
            }
            else {
                player.setJailTurnCount(player.getJailTurnCount() + 1);
            }
        nextTurn();
        ui.updateBoardState();


        // if user clicks at end turn --> nextTurn(); this is business of ui controller, not this class
    }

    public void nextTurn() {
        if ( !diceResult.isDouble() || doubleCount == 3 ) {
            playerController.switchToNextPlayer();
            ui.sendObject("next player:" + playerController.getActivePlayerIndex());
            doubleCount = 0;
        }
        turn++;
    }

    public void processPropertyTile(PropertyTile tile) {
        Property property = board.getPropertyById(tile.getPropertyId());

        // actionLog.addMessage(getActivePlayer().getName() + " lands on " + property.getName() + "\n");
        if (!property.isOwned() && getActivePlayer().getBalance() >= property.getPrice()) {
            boolean playerBoughtProperty = ui.showPropertyDialog(property); // separate the house and hotel dialog

            if (playerBoughtProperty) {
                BuyPropertyAction action = new BuyPropertyAction(property.getId(), getActivePlayer().getPlayerId());
                action.act();
                ui.sendAction(action);
            }
            else {
                // auction --> iteration 2
            }
        }
        else if ( property.isOwned() && getActivePlayer().getPlayerId() != property.getOwnerId() ){
            int transferAmount = 0;
            Player propertyOwner = playerController.getById(property.getOwnerId());
            System.out.println("Property owner: " + propertyOwner.getName());
            int ownerTeamNo = propertyOwner.getTeamNumber();
            int activeTeamNo = getActivePlayer().getTeamNumber();
            boolean diffTeams = mode.isAlliance() && (ownerTeamNo != activeTeamNo);
            if (diffTeams) {
                if (property instanceof Dorm) {
                    System.out.println("Dorm count: " + propertyOwner.getProperties().get("DORM").size());
                    if ( propertyOwner.getProperties().get("DORM").size() == 1 ) {
                        transferAmount = 2500;
                    }
                    else if (propertyOwner.getProperties().get("DORM").size() == 2 ){
                        transferAmount = 5000;
                    }
                    else if ( propertyOwner.getProperties().get("DORM").size() == 3 ) {
                        transferAmount = 10000;
                    }
                    else if ( propertyOwner.getProperties().get("DORM").size() == 4 ) {
                        transferAmount = 20000;
                    }
                }
                else if (property instanceof Facility) {
                    int diceTotal = diceResult.getValue();
                    if ( propertyOwner.getProperties().get("FACILITY").size() == 1 ) {
                        transferAmount = diceTotal * 400;
                    }
                    else if ( propertyOwner.getProperties().get("FACILITY").size() == 2 ) {
                        transferAmount = diceTotal * 1000;
                    }
                }
                else if (property instanceof Building ) {
                    Building building = (Building) property;
                    boolean isComplete = propertyOwner.isComplete(building);

                    if ( building.getClassroomCount() == 0 && !isComplete ) {
                        transferAmount = building.getRents().get(0);
                    }
                    else if ( building.getClassroomCount() == 0 && isComplete ) {
                        transferAmount = building.getRents().get(0) * 2;
                    }
                    else if ( building.getLectureHallCount() == 1 ) {
                        transferAmount = building.getRents().get(5);
                    }
                    else if ( building.getClassroomCount() == 1 ) {
                        transferAmount = building.getRents().get(1);
                    }
                    else if ( building.getClassroomCount() == 2 ) {
                        transferAmount = building.getRents().get(2);
                    }
                    else if ( building.getClassroomCount() == 3 ) {
                        transferAmount = building.getRents().get(3);
                    }
                    else if ( building.getClassroomCount() == 4 ) {
                        transferAmount = building.getRents().get(4);
                    }
                }

                TransferAction transferAction = new TransferAction(getActivePlayer().getPlayerId(), propertyOwner.getPlayerId(), transferAmount);
                transferAction.act();
                ui.sendAction(transferAction);
            }
/*
            if (property instanceof Dorm) {
                System.out.println("Dorm count: " + propertyOwner.getProperties().get("DORM").size());
                if ( propertyOwner.getProperties().get("DORM").size() == 1 ) {
                    transferAmount = 2500;
                }
                else if (propertyOwner.getProperties().get("DORM").size() == 2 ){
                    transferAmount = 5000;
                }
                else if ( propertyOwner.getProperties().get("DORM").size() == 3 ) {
                    transferAmount = 10000;
                }
                else if ( propertyOwner.getProperties().get("DORM").size() == 4 ) {
                    transferAmount = 20000;
                }
            }
            else if (property instanceof Facility) {
                int diceTotal = diceResult.getValue();
                if ( propertyOwner.getProperties().get("FACILITY").size() == 1 ) {
                    transferAmount = diceTotal * 400;
                }
                else if ( propertyOwner.getProperties().get("FACILITY").size() == 2 ) {
                    transferAmount = diceTotal * 1000;
                }
            }
            else if (property instanceof Building ) {
                Building building = (Building) property;
                boolean isComplete = propertyOwner.isComplete(building);

                if ( building.getClassroomCount() == 0 && !isComplete ) {
                    transferAmount = building.getRents().get(0);
                }
                else if ( building.getClassroomCount() == 0 && isComplete ) {
                    transferAmount = building.getRents().get(0) * 2;
                }
                else if ( building.getLectureHallCount() == 1 ) {
                    transferAmount = building.getRents().get(5);
                }
                else if ( building.getClassroomCount() == 1 ) {
                    transferAmount = building.getRents().get(1);
                }
                else if ( building.getClassroomCount() == 2 ) {
                    transferAmount = building.getRents().get(2);
                }
                else if ( building.getClassroomCount() == 3 ) {
                    transferAmount = building.getRents().get(3);
                }
                else if ( building.getClassroomCount() == 4 ) {
                    transferAmount = building.getRents().get(4);
                }
            }

            TransferAction transferAction = new TransferAction(getActivePlayer().getPlayerId(), propertyOwner.getPlayerId(), transferAmount);
            transferAction.act();
            ui.sendAction(transferAction);
*/
        }
    }

    public Card processChanceCardTile() {
        Card card = board.drawChanceCard();
        DrawChanceCardAction action = new DrawChanceCardAction(getActivePlayer(), card);
        action.act();
        ui.sendAction(action);
        //ui.showCard(card); this is business of control object
        processCard(card);

        return card; // return to ui? Card card = monopolyGame.processChanceCardTile()
        //
    }

    public Card processCommunityChestCardTile() {
        Card card = board.drawCommunityChestCard();
        DrawCommunityChestCardAction action = new DrawCommunityChestCardAction(getActivePlayer(), card);
        action.act();
        ui.sendAction(action);
        // ui.showCard(card); this is business of control object
        processCard(card);

        return card; // return to ui?
    }

    public void processCard(Card card) {
        Player activePlayer = getActivePlayer();
        switch (card.getId()) {
            case 0:
                FreeMoveAction freeMoveAction2 = new FreeMoveAction(activePlayer.getPlayerId(), 1); // L Building --> position = 1
                freeMoveAction2.act();
                ui.sendAction(freeMoveAction2);
                //monopolyGame.processTurn();
                break;
            case 1:
                AddMoneyAction addMoneyAction = new AddMoneyAction(activePlayer.getPlayerId(), 1_000);
                addMoneyAction.act();
                ui.sendAction(addMoneyAction);
                break;
            case 2:
                // requires ui confirmation
                break;
            case 3:
                RemoveMoneyAction removeMoneyAction = new RemoveMoneyAction(activePlayer.getPlayerId(), 10_000);
                removeMoneyAction.act();
                ui.sendAction(removeMoneyAction);
                break;
            case 4:
            case 8: // duplicate card with 4
                FreeMoveAction freeMoveAction = new FreeMoveAction(activePlayer.getPlayerId(), 0);
                freeMoveAction.act();
                ui.sendAction(freeMoveAction);
                break;
            case 5:
                for (Player p : getPlayerController().getPlayers() )
                    if ( p.getPlayerId() != activePlayer.getPlayerId() ) {
                        TransferAction transferAction = new TransferAction(p.getPlayerId(), activePlayer.getPlayerId(), 1_000);
                        transferAction.act();
                        ui.sendAction(transferAction);
                    }
                break;
            case 6:
            case 11:
                GoToJailAction goToJailAction = new GoToJailAction(activePlayer.getPlayerId()); // player moves again if he threw double before this
                goToJailAction.act();
                ui.sendAction(goToJailAction);
                break;
            case 7:
                GetOutOfJailAction getOutOfJailAction = new GetOutOfJailAction(activePlayer.getPlayerId()); // ToDo store the card??
                getOutOfJailAction.act();
                ui.sendAction(getOutOfJailAction);
                break;
            case 9:
                MoveAction moveAction = new MoveAction(activePlayer.getPlayerId(), -3);
                moveAction.act();
                ui.sendAction(moveAction);
                //monopolyGame.processTurn();
                break;
            case 10:
                FreeMoveAction freeMoveAction1 = new FreeMoveAction(activePlayer.getPlayerId(), 23); // Kirac is in 23th tile
                freeMoveAction1.act();
                ui.sendAction(freeMoveAction1);
                //monopolyGame.processTurn(); // ToDo seperate processturn and processtile
                break;
            case 12:
                FreeMoveAction freeMoveAction3 = new FreeMoveAction(activePlayer.getPlayerId(),39); // Library is the last tile (39th)
                freeMoveAction3.act();
                ui.sendAction(freeMoveAction3);
                //monopolyGame.processTurn();
                break;
            case 13:
                AddMoneyAction addMoneyAction1 = new AddMoneyAction(activePlayer.getPlayerId(), 10_000);
                addMoneyAction1.act();
                ui.sendAction(addMoneyAction1);
                break;
            case 14:
                RemoveMoneyAction removeMoneyAction1 = new RemoveMoneyAction(activePlayer.getPlayerId(), 2_000);
                removeMoneyAction1.act();
                ui.sendAction(removeMoneyAction1);
                break;
            case 15:
                int houseCount = 0;
                int hotelCount = 0;
                int repairAmount = 0;
                ArrayList<Property> properties = activePlayer.getProperties().get("BUILDING");
                for (Property p : properties ) {
                    houseCount = houseCount + ((Building) p).getClassroomCount();
                    hotelCount = hotelCount + ((Building) p).getLectureHallCount();
                }
                repairAmount = (4_000 * houseCount) + (11_500 * hotelCount);
                RemoveMoneyAction removeMoneyAction2 = new RemoveMoneyAction(activePlayer.getPlayerId(), repairAmount);
                removeMoneyAction2.act();
                ui.sendAction(removeMoneyAction2);
                break;
        }
    }

    public boolean isGameOver() {
        ArrayList<Player> players = playerController.getPlayers();

        int bankruptPlayerCount = 0;

        for (Player p : players) {
            if (p.isBankrupt()) {
                bankruptPlayerCount++;
            }
        }

        return bankruptPlayerCount == 3;
    }

    /**
     * Private helper function that computes the moveCount for all cases, including speedDie
     * For bus and freeMove, the user needs to enter some input, so we use integers to
     * differentiate between BUS and freeMove. Will return 0 if and only if
     * player rolled Mr. Monopoly and there were no property to land
     * @return -1 for BUS, -2 for freeMove, computed move count for other cases
     */
    private int computeMoveCount() {
        if (diceResult.getSpeedDieResult().isBus()) {
            return -1;
        }
        else if (diceResult.getSpeedDieResult().isMrMonopoly()) {
            return computeMrMonopoly();
        }
        else if (diceResult.isTriple()) {
            return -2;
        }
        else {
            return diceResult.getValue();
        }
    }

    /**
     * The algorithm that computes the number of moves
     * the player needs to make to use MrMonopoly
     * AFTER playing their dice
     * There is a slight chance that the player
     * has no chance to move on next property
     * so they stay where they are
     * @return number of moves, return 0 for the edge case
     */
    private int computeMrMonopoly() {
        int activePID = playerController.getActivePlayerIndex();
        int initActivePPos = PlayerController.getById(activePID).getPosition();
        int activePPos = initActivePPos + 1;
        // Iterate through the board, start at active players position,
        // stop at one tile behind and take module of # of tiles so that tileID is always found
        // even if player passes the start tile
        // Detail1 : The player can't land the tile they are currently on if they rolled Mr. Monopoly
        // , even if it satisfies the conditions of this algorithm
        for (; activePPos != initActivePPos - 1; activePPos = (activePPos + 1) % board.getTiles().size() ) {
            Property posProp = getPropertyByTileID(activePPos);
            // Skip tiles other than property tiles
            if (posProp == null)
                continue;
            // Case 1: No property is left in the bank
            // Player moves to the next property where he/she will pay rent
            if (board.isAllOwned()) {
                // if the players are different AND diff. teams AND unmortgaged
                int ownerID = posProp.getOwnerId();
                Player owner = PlayerController.getById(ownerID);
                boolean diffPlayers = ownerID != activePID;
                boolean diffTeams = getActivePlayer().getTeamNumber() != owner.getTeamNumber();
                boolean mortgaged = posProp.isMortgaged();
                if (diffPlayers && diffTeams && !mortgaged) {
                    return activePPos - initActivePPos;
                }
            }
            // Case 2 : There are unowned properties left in the bank
            // Player moves to the next unowned property
            else {
                if (!posProp.isOwned()) {
                    return activePPos - initActivePPos;
                }
            }
            continue;
        }
        // IF there is literally no property to pay rent in the game
        // player doesn't move
        return 0;
    }

    /**
     * Gets the property of a given tile ID if a given tile ID is a PropertyTile
     * Else, returns null
     * @param tileID ID of the given tile
     * @return Property for that tile, else null
     */
    private Property getPropertyByTileID(int tileID) {
        Tile curTile = board.getTiles().get(tileID);
        if (curTile instanceof PropertyTile) {
            return board.getProperties().get(((PropertyTile) curTile).getPropertyId());
        }
        return null;
    }

    public void update() {
        ui.updateBoardState();
    }

    public void bankruptPlayer(Player player) {
        player.setBankrupt(true);
    }

    public Player getActivePlayer() {return playerController.getActivePlayer();}

    public Board getBoard() {
        return board;
    }

    public void setBoard(Board board) {
        this.board = board;
    }

    public int getTurn() {
        return turn;
    }

    public void setTurn(int turn) {
        this.turn = turn;
    }

    public static ActionLog getActionLog() {
        return ActionLog.getInstance();
    }

    public void setActionLog(ActionLog actionLog) {
        MonopolyGame.actionLog = actionLog;
    }

    public PlayerController getPlayerController() {
        return playerController;
    }

    public void setPlayerController(PlayerController playerController) {
        this.playerController = playerController;
    }

    public Dice getDice() {
        return dice;
    }

    public void setDice(Dice dice) {
        this.dice = dice;
    }

    public boolean isGameStarted() {
        return gameStarted;
    }

    public void setGameStarted(boolean gameStarted) {
        this.gameStarted = gameStarted;
    }

    public GameScreenController getUi() {
        return ui;
    }

    public void setUi(GameScreenController ui) {
        this.ui = ui;
    }

    /*    public static void main(String[] args) throws IOException {
        Player player1 = new Player(1, "Mehmet" , Player.Token.BATTLESHIP, 1);
        Player player2 = new Player(2, "Ali" , Player.Token.BATTLESHIP, 1);
        Player player3 = new Player(3, "Veli" , Player.Token.BATTLESHIP, 1);

        ArrayList<Player> players = new ArrayList<>();
        players.add(player1);
        players.add(player2);
        players.add(player3);

        MonopolyGame monopolyGame = new MonopolyGame(players);
        monopolyGame.startGame();

        for ( int i = 0; i < 4; i++ ) {
            System.out.println("Active player: " + monopolyGame.getActivePlayer().getName());
            monopolyGame.rollDice();
            monopolyGame.processTurn();
            monopolyGame.nextTurn();
            System.out.println(actionLog.toString());
            System.out.println("---------------------------");
        }

    }*/
}
