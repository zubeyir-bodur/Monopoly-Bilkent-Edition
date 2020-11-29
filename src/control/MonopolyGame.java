package control;

import control.action.*;
import entity.Board;
import entity.Player;
import entity.card.Card;
import entity.dice.Dice;
import entity.dice.DiceResult;
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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Random;

public class MonopolyGame {

    Board board;
    int turn;
    static ActionLog actionLog;
    PlayerController playerController;
    // GameMode gameMode;
    boolean gameStarted = false;
    boolean gamePaused = false;
    int doubleCount = 0;
    int moveCount = 0; // sum of dices
    Dice dice;
    DiceResult diceResult;
    GameScreenController ui;

    public MonopolyGame(ArrayList<Player> players, GameScreenController ui) throws IOException {
        board = new Board();
        turn = 0;
        actionLog = new ActionLog();
        playerController = new PlayerController(players);
        dice = new Dice(System.currentTimeMillis());
        this.ui = ui;
    }

    public void addPlayer(Player player) {
        playerController.addPlayer(player);
    }

    public void stopGame() {
        gameStarted = false;
    }

    public void startGame() {
        ArrayList<Player> players = playerController.getPlayers();
        playerController.setActivePlayer(players.get(new Random().nextInt(players.size() - 1))); // randomize the process
        gameStarted = true;
    }

    public DiceResult rollDice() {
        diceResult = dice.roll(false); // false for not-speedDie

        if ( diceResult.isDouble() ) {
            doubleCount++;
        }

        actionLog.addMessage(getActivePlayer().getName() + " rolls dice and gets " + diceResult.getFirstDieResult()
                + ", " + diceResult.getSecondDieResult() + "\n");

        moveCount = diceResult.getFirstDieResult() + diceResult.getSecondDieResult();
        return diceResult; // return because ui will show this result
    }

    public void processTurn() {
        Player player = playerController.getActivePlayer();

        if (doubleCount == 3) { // if this is the third double, put player into the jail
            new GoToJailAction(player).act();
            doubleCount = 0;
        }
        else if ((diceResult.isDouble() && player.isInJail()) || (player.getJailTurnCount() == 3)) {
            new GetOutOfJailAction(player).act();
            player.setJailTurnCount(0);
        }
        else {
            if (!player.isInJail()) {
                new MoveAction(player, moveCount).act(); // try catch? PlayerIsInJailException

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
                    new RemoveMoneyAction(player, taxTile.getAmount()).act();
                }
                else if (tile instanceof GoToJailTile) {
                    new GoToJailAction(player).act();
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
        }
        nextTurn();
        ui.updateBoardState();


        // if user clicks at end turn --> nextTurn(); this is business of ui controller, not this class
    }

    public void nextTurn() {
        if ( !diceResult.isDouble() || doubleCount == 3 ) {
            playerController.switchToNextPlayer();
            doubleCount = 0;
        }
        turn++;
    }

    public void processPropertyTile(PropertyTile tile) {
        Property property = board.getPropertyById(tile.getPropertyId());

        actionLog.addMessage(getActivePlayer().getName() + " lands on " + property.getName() + "\n");
        if (!property.isOwned() && getActivePlayer().getBalance() >= property.getPrice()) {
            boolean playerBoughtProperty = ui.showPropertyDialog(property);

            System.out.println("Answer: " + playerBoughtProperty);

            if (playerBoughtProperty) {
                new BuyPropertyAction(property, getActivePlayer()).act();
            }
            else {
                // auction --> iteration 2
            }
        }
        else if ( property.isOwned() && getActivePlayer().getPlayerId() != property.getOwnerId() ){
            int transferAmount = 0;
            Player propertyOwner = playerController.getById(property.getOwnerId());
            System.out.println("Property owner: " + propertyOwner.getName());

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
                int diceTotal = diceResult.getFirstDieResult() + diceResult.getSecondDieResult();
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

                if ( building.getHouseCount() == 0 && !isComplete ) {
                    transferAmount = building.getRents().get(0);
                }
                else if ( building.getHouseCount() == 0 && isComplete ) {
                    transferAmount = building.getRents().get(0) * 2;
                }
                else if ( building.getHotelCount() == 1 ) {
                    transferAmount = building.getRents().get(5);
                }
                else if ( building.getHouseCount() == 1 ) {
                    transferAmount = building.getRents().get(1);
                }
                else if ( building.getHouseCount() == 2 ) {
                    transferAmount = building.getRents().get(2);
                }
                else if ( building.getHouseCount() == 3 ) {
                    transferAmount = building.getRents().get(3);
                }
                else if ( building.getHouseCount() == 4 ) {
                    transferAmount = building.getRents().get(4);
                }
            }

            new TransferAction(getActivePlayer(), propertyOwner, transferAmount).act();
        }
    }

    public void buyProperty(PropertyTile tile) {
        new BuyPropertyAction(board.getProperties().get(tile.getPropertyId()), getActivePlayer()).act();
    }

    public Card processChanceCardTile() {
        Card card = board.drawChanceCard();
        new DrawChanceCardAction(getActivePlayer(), card).act();
        //ui.showCard(card); this is business of control object
        card.processCard(this);

        return card; // return to ui? Card card = monopolyGame.processChanceCardTile()
        //
    }

    public Card processCommunityChestCardTile() {
        Card card = board.drawCommunityChestCard();
        new DrawCommunityChestCardAction(getActivePlayer(), card).act();
        // ui.showCard(card); this is business of control object
        card.processCard(this);

        return card; // return to ui?
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
        return actionLog;
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
