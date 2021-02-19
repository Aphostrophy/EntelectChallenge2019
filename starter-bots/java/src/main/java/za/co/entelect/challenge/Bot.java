package za.co.entelect.challenge;

import za.co.entelect.challenge.command.*;
import za.co.entelect.challenge.entities.*;
import za.co.entelect.challenge.enums.*;

import java.sql.SQLOutput;
import java.util.*;
import java.util.stream.*;

public class Bot {

    private Random random;
    private GameState gameState;
    private Opponent opponent;
    private MyWorm currentWorm;
    private Worm[] wormsData;

    private class CellDistance {
        Cell cell;
        int distance;

        public int getDistance() {
            return distance;
        }

        CellDistance(Cell cell, int distance){
            this.cell = cell;
            this.distance = distance;
        }
    }

    private class CellTurn {
        Cell cell;
        int turns;

        public int getTurns() {return turns;}

        CellTurn(Cell cell, int turns) {
            this.cell = cell;
            this.turns = turns;
        }
    }

    public Bot(Random random, GameState gameState) {
        this.random = random;
        this.gameState = gameState;
        this.opponent = gameState.opponents[0];
        this.currentWorm = getCurrentWorm(gameState);
        this.wormsData = gameState.myPlayer.worms;
    }

    private MyWorm getCurrentWorm(GameState gameState) {
        return Arrays.stream(gameState.myPlayer.worms)
                .filter(myWorm -> myWorm.id == gameState.currentWormId)
                .findFirst()
                .get();
    }

    public Command run() {

        Worm enemyWorm = getFirstWormInRange(currentWorm);
        Utilities utilities = new Utilities();
        if(currentWorm.id == 2 && currentWorm.bananaBombs.count>0){
            for(Worm worm : gameState.opponents[0].worms){
                if(euclideanDistance(currentWorm.position.x,currentWorm.position.y,worm.position.x,worm.position.y) <=5){
                    Direction direction = resolveDirection(currentWorm.position,worm.position);
                    return BananaBombStrategy(worm, direction);
                }
            }
        }

        int lastid = currentWorm.id - 1;

        if(gameState.myPlayer.previousCommand.contains("shoot") && gameState.myPlayer.remainingWormSelections > 0 && getFirstWormInRange(gameState.myPlayer.worms[lastid==0 ? 2 : lastid-1])!=null && gameState.myPlayer.worms[lastid==0 ? 2 : lastid-1].roundsUntilUnfrozen==0){
            return ForceShootStrategy(gameState.myPlayer.worms[lastid==0 ? 2 : lastid-1]);
        }

        if (enemyWorm != null) {

            Cell cell = gameState.map[currentWorm.position.y][currentWorm.position.x];

            if (cell.type == CellType.LAVA) {
                return EscapeLavaStrategy();
            } else if((utilities.calculateMyWormsHealth(gameState)>utilities.calculateEnemyWormsHealth(gameState) && utilities.countMyWorms(gameState) > utilities.countEnemyWorms(gameState)) || (utilities.countMyWorms(gameState)==3)){
                return AttackStrategy(enemyWorm);
            } else if (enemyWorm.id == opponent.currentWormId && enemyWorm.roundsUntilUnfrozen == 0) {
                return escapeShootStrategy();
            } else if(utilities.countMyWorms(gameState)==1 && utilities.calculateEnemyWormsHealth(gameState)>utilities.calculateMyWormsHealth(gameState)) {
                return escapeShootStrategy();
            } else{
                return AttackStrategy(enemyWorm);
//                return EscapeShootStrategy(enemyWorm);
            }
        }

        Cell destBlock = getNearestObjective(currentWorm.position.x, currentWorm.position.y).get(0).cell;

        if(currentWorm.id == 1) {
            if(getNearestPowerup(currentWorm.position.x,currentWorm.position.y) != null){
                destBlock = getNearestPowerup(currentWorm.position.x,currentWorm.position.y).get(0).cell;
                if(destBlock == null) {
                    destBlock = getNearestEnemy(currentWorm.position.x, currentWorm.position.y);
                }
            }
        } else if(currentWorm.id == 2) {
            List<Cell> surroundingBlocks = getSurroundingCells(currentWorm.position.x, currentWorm.position.y);
            destBlock = hunt(2);
            if(destBlock==null){
                destBlock = getNearestEnemy(currentWorm.position.x, currentWorm.position.y);
            }
        } else if(currentWorm.id == 3) {
                if(destBlock == null || euclideanDistance(currentWorm.position.x, currentWorm.position.y, destBlock.x, destBlock.y) <= 3) {
                    destBlock = getNearestEnemy(currentWorm.position.x, currentWorm.position.y);
                }
        }
        return moveToObjective(currentWorm.position.x, currentWorm.position.y, destBlock);
    }

    private Worm getFirstWormInRange(MyWorm currentWorm) {

        Set<String> cells = constructFireDirectionLines(currentWorm.weapon.range)
                .stream()
                .flatMap(Collection::stream)
                .map(cell -> String.format("%d_%d", cell.x, cell.y))
                .collect(Collectors.toSet());

        for (Worm enemyWorm : opponent.worms) {
            if(enemyWorm.health>0){
                String enemyPosition = String.format("%d_%d", enemyWorm.position.x, enemyWorm.position.y);
                if (cells.contains(enemyPosition)) {
                    return enemyWorm;
                }
            }
        }

        return null;
    }

    private Worm getFirstWormInRangeMarkII(MyWorm c) {

        for (Worm enemyWorm : opponent.worms) {
            if(enemyWorm.health>0 && euclideanDistance(c.position.x,c.position.y,enemyWorm.position.x,enemyWorm.position.y) <=4){
                return enemyWorm;
            }
        }

        return null;
    }

    private List<List<Cell>> constructFireDirectionLines(int range) {
        List<List<Cell>> directionLines = new ArrayList<>();
        for (Direction direction : Direction.values()) {
            List<Cell> directionLine = new ArrayList<>();
            for (int directionMultiplier = 1; directionMultiplier <= range; directionMultiplier++) {

                int coordinateX = currentWorm.position.x + (directionMultiplier * direction.x);
                int coordinateY = currentWorm.position.y + (directionMultiplier * direction.y);

                if (!isValidCoordinate(coordinateX, coordinateY)) {
                    break;
                }

                if (euclideanDistance(currentWorm.position.x, currentWorm.position.y, coordinateX, coordinateY) > range) {
                    break;
                }

                Cell cell = gameState.map[coordinateY][coordinateX];
                if (cell.type != CellType.AIR) {
                    break;
                }

                directionLine.add(cell);
            }
            directionLines.add(directionLine);
        }

        return directionLines;
    }

    private List<Cell> getSurroundingCells(int x, int y) {
        Utilities utilities = new Utilities();
        ArrayList<Cell> cells = new ArrayList<>();
        for (int i = x - 1; i <= x + 1; i++) {
            for (int j = y - 1; j <= y + 1; j++) {
                // Don't include the current position
                if ((i != x || j != y) && isValidCoordinate(i, j) && gameState.map[j][i].type!=CellType.DEEP_SPACE) {
                    cells.add(gameState.map[j][i]);
                }
            }
        }
        return cells;
    }

    private List<CellDistance> getNearestPath(List<Cell> surroundingBlocks, int destX, int destY) {
        List<CellDistance> distanceList = new ArrayList<CellDistance>();

        for(int i = 0; i < surroundingBlocks.size(); i++) {
            CellDistance d = new CellDistance(surroundingBlocks.get(i), euclideanDistance(surroundingBlocks.get(i).x,surroundingBlocks.get(i).y, destX, destY));
            distanceList.add(d);
        }

        List<CellDistance> sortedDistance = distanceList.stream()
                .sorted(Comparator.comparing(CellDistance::getDistance))
                .collect(Collectors.toList());

        return sortedDistance;
    }

    private int calculateTurnToDest(int initialX, int initialY, int destX, int destY) {
        int total = 0;
        List<Cell> surroundingBlocks = getSurroundingCells(initialX, initialY);
        Cell block = getNearestPath(surroundingBlocks, destX, destY).get(0).cell;

        while(block.x != destX || block.y != destY) {
            if(block.type == CellType.DIRT) {
                total += 2;
            } else if(block.type == CellType.AIR) {
                total += 1;
            }
            surroundingBlocks = getSurroundingCells(block.x, block.y);
            block = getNearestPath(surroundingBlocks, destX, destY).get(0).cell;
        }
        return total;
    }

    private List<CellTurn> getNearestObjective(int currPosX, int currPosY) {
        Utilities utilities = new Utilities();
        List<Cell> objectives = new ArrayList<Cell>();

        for(int i = 0; i < 33; i++) {
            for(int j = 0; j < 33; j++) {
                if((utilities.isOccupied(gameState,i,j) && gameState.map[j][i].occupier.playerId == opponent.id) || gameState.map[j][i].powerup != null) {
                    objectives.add(gameState.map[j][i]);
                }
            }
        }

        List<CellTurn> objectivesDistance = new ArrayList<CellTurn>();
        for(int i = 0; i < objectives.size(); i++) {
            CellTurn t = new CellTurn(objectives.get(i), calculateTurnToDest(currPosX, currPosY, objectives.get(i).x, objectives.get(i).y));
            objectivesDistance.add(t);
        }


        List<CellTurn> sortedObjectives = objectivesDistance.stream()
                .sorted(Comparator.comparing(CellTurn::getTurns))
                .collect(Collectors.toList());


        return sortedObjectives;
    }

    private Cell getNearestDirt(int currPosX, int currPosY) {
        List<Cell> objectives = new ArrayList<Cell>();

        for(int i = 0; i < 33; i++) {
            for(int j = 0; j < 33; j++) {
                if(gameState.map[j][i].type == CellType.DIRT) {
                    objectives.add(gameState.map[j][i]);
                }
            }
        }

        List<CellDistance> distanceList = new ArrayList<CellDistance>();

        for(int i = 0; i < objectives.size(); i++) {
            CellDistance d = new CellDistance(objectives.get(i), euclideanDistance(objectives.get(i).x, objectives.get(i).y, currPosX, currPosY));
            distanceList.add(d);
        }

        List<CellDistance> sortedDistance = distanceList.stream()
                .sorted(Comparator.comparing(CellDistance::getDistance))
                .collect(Collectors.toList());

        return sortedDistance.get(0).cell;
    }

    private Cell getNearestEnemy(int currPosX, int currPosY) {
        List<Cell> objectives = new ArrayList<Cell>();

        for(int i = 0; i < 33; i++) {
            for(int j = 0; j < 33; j++) {
                if(gameState.map[j][i].occupier != null && gameState.map[j][i].occupier.playerId == opponent.id) {
                    objectives.add(gameState.map[j][i]);
                }
            }
        }

        List<CellTurn> objectivesDistance = new ArrayList<CellTurn>();
        for(int i = 0; i < objectives.size(); i++) {
            CellTurn t = new CellTurn(objectives.get(i), calculateTurnToDest(currPosX, currPosY, objectives.get(i).x, objectives.get(i).y));
            objectivesDistance.add(t);
        }

        List<CellTurn> sortedObjectives = objectivesDistance.stream()
                .sorted(Comparator.comparing(CellTurn::getTurns))
                .collect(Collectors.toList());

        return sortedObjectives.get(0).cell;
    }

    private List<CellTurn> getNearestPowerup(int currPosX, int currPosY) {
        List<Cell> objectives = new ArrayList<Cell>();

        for(int i = 0; i < 33; i++) {
            for(int j = 0; j < 33; j++) {
                if(gameState.map[j][i].powerup != null) {
                    objectives.add(gameState.map[j][i]);
                }
            }
        }

        if(objectives.size() == 0) {return null;}

        List<CellTurn> objectivesDistance = new ArrayList<CellTurn>();
        for(int i = 0; i < objectives.size(); i++) {
            CellTurn t = new CellTurn(objectives.get(i), calculateTurnToDest(currPosX, currPosY, objectives.get(i).x, objectives.get(i).y));
            objectivesDistance.add(t);
        }

        List<CellTurn> sortedObjectives = objectivesDistance.stream()
                .sorted(Comparator.comparing(CellTurn::getTurns))
                .collect(Collectors.toList());

        return sortedObjectives;

    }

    private Command moveToObjective(int initialX, int initialY, Cell destination) {
        List<Cell> surroundingBlocks = getSurroundingCells(initialX, initialY);
        List<CellDistance> chosenBlocks = getNearestPath(surroundingBlocks, destination.x, destination.y);

        Utilities utilities = new Utilities();

        for(CellDistance blocks : chosenBlocks){
            Cell chosenBlock = blocks.cell;
            if (chosenBlock.type == CellType.AIR && !utilities.isOccupied(gameState,chosenBlock.x,chosenBlock.y)) {
                return new MoveCommand(chosenBlock.x, chosenBlock.y);
            } else if (chosenBlock.type == CellType.DIRT) {
                return new DigCommand(chosenBlock.x, chosenBlock.y);
            } else if (chosenBlock.type==CellType.LAVA) {
                return EscapeLavaStrategy();
            }
        }

        return escapeShootStrategy();
    }

    private Cell follow(int id) {
        for(int i = 0; i < 33; i++) {
            for(int j = 0; j < 33; j++) {
                if(gameState.map[j][i].occupier != null &&
                        gameState.map[j][i].occupier.id == id &&
                        gameState.map[j][i].occupier.playerId == gameState.myPlayer.id &&
                        gameState.map[j][i].occupier.health > 0) {
                    return gameState.map[j][i];
                }
            }
        }
        return null;
    }

    private Cell hunt(int id) {
        for(int i = 0; i < 33; i++) {
            for(int j = 0; j < 33; j++) {
                if(gameState.map[j][i].occupier != null &&
                        gameState.map[j][i].occupier.id == id &&
                        gameState.map[j][i].occupier.playerId == opponent.id &&
                        gameState.map[j][i].occupier.health > 0) {
                    return gameState.map[j][i];
                }
            }
        }
        return null;
    }

    private int euclideanDistance(int aX, int aY, int bX, int bY) {
        return (int) (Math.sqrt(Math.pow(aX - bX, 2) + Math.pow(aY - bY, 2)));
    }

    private boolean isValidCoordinate(int x, int y) {
        return x >= 0 && x < gameState.mapSize
                && y >= 0 && y < gameState.mapSize;
    }

    private Direction resolveDirection(Position a, Position b) {
        StringBuilder builder = new StringBuilder();

        int verticalComponent = b.y - a.y;
        int horizontalComponent = b.x - a.x;

        if (verticalComponent < 0) {
            builder.append('N');
        } else if (verticalComponent > 0) {
            builder.append('S');
        }

        if (horizontalComponent < 0) {
            builder.append('W');
        } else if (horizontalComponent > 0) {
            builder.append('E');
        }

        return Direction.valueOf(builder.toString());
    }

    private Command ForceShootStrategy(MyWorm c){
        Worm nearestEnemyWorm = getFirstWormInRangeMarkII(c);

        if(nearestEnemyWorm!=null){
            Direction direction = resolveDirection(c.position, nearestEnemyWorm.position);
            return new ForceShootCommand(direction,c.id);
        }
        return EscapeLavaStrategy();
    }

    private Command EscapeLavaStrategy(){

        Utilities utilities = new Utilities();

        int x = currentWorm.position.x;
        int y = currentWorm.position.y;
        int moveX = x<33/2 ? x+1 : x-1;
        int moveY = y<33/2 ? y+1 : y-1;
        boolean conflict = utilities.isPathInvalid(currentWorm,gameState,moveX,moveY);
        if(conflict){
            moveX = x<33/2 ? x+1 : x-1;
            moveY = y;
            conflict = utilities.isPathInvalid(currentWorm,gameState,moveX,moveY);
            if(conflict){
                moveX = x;
                moveY = y<33/2 ? y+1 : y-1;
                conflict = utilities.isPathInvalid(currentWorm,gameState,moveX,moveY);
                if(conflict){
                    moveX = x<33/2 ? x-1 : x+1;
                    moveY = y<33/2 ? y+1 : y-1;
                    conflict = utilities.isPathInvalid(currentWorm,gameState,moveX,moveY);
                    if(conflict){
                        moveX = x<33/2 ? x+1 : x-1;
                        moveY = y<33/2 ? y-1 : y+1;
                        conflict = utilities.isPathInvalid(currentWorm,gameState,moveX,moveY);
                        if(conflict){
                            moveX = x;
                            moveY = y<33/2 ? y-1 : y+1;
                            conflict = utilities.isPathInvalid(currentWorm,gameState,moveX,moveY);
                            if(conflict){
                                moveX = x<33/2 ? x-1 : x+1;
                                moveY = y;
                                if(conflict){
                                    return new DoNothingCommand();
                                }
                            }
                        }
                    }
                }
            }
        }
        return new MoveCommand(moveX,moveY);
    }

    private Command EscapeLavaStrategy(Worm enemyWorm){

//        for(int i = x - 1; i <= x + 1; i++){
//            if(x == enemyX && i == x){
//                continue;
//            }
//
//            for(int j = y - 1; j <= y + 1; j++){
//                if(y == enemyY && j == y) {
//                    continue;
//                }
        Utilities utilities = new Utilities();

        int x = currentWorm.position.x;
        int y = currentWorm.position.y;
        int moveX = x<33/2 ? x+1 : x-1;
        int moveY = y<33/2 ? y+1 : y-1;
        boolean conflict = utilities.isPathInvalid(currentWorm,gameState,moveX,moveY);
        if(conflict){
            moveX = x<33/2 ? x+1 : x-1;
            moveY = y;
            conflict = utilities.isPathInvalid(currentWorm,gameState,moveX,moveY);
            if(conflict){
                moveX = x;
                moveY = y<33/2 ? y+1 : y-1;
                conflict = utilities.isPathInvalid(currentWorm,gameState,moveX,moveY);
                if(conflict){
                    moveX = x<33/2 ? x-1 : x+1;
                    moveY = y<33/2 ? y+1 : y-1;
                    conflict = utilities.isPathInvalid(currentWorm,gameState,moveX,moveY);
                    if(conflict){
                        moveX = x<33/2 ? x+1 : x-1;
                        moveY = y<33/2 ? y-1 : y+1;
                        conflict = utilities.isPathInvalid(currentWorm,gameState,moveX,moveY);
                        if(conflict){
                            moveX = x;
                            moveY = y<33/2 ? y-1 : y+1;
                            conflict = utilities.isPathInvalid(currentWorm,gameState,moveX,moveY);
                            if(conflict){
                                moveX = x<33/2 ? x-1 : x+1;
                                moveY = y;
                                if(conflict){
                                    return AttackStrategy(enemyWorm);
                                }
                            }
                        }
                    }
                }
            }
        }
        return new MoveCommand(moveX,moveY);
    }

    private Command AttackAnotherWorm(Worm enemyWorm) {
        Utilities utilities = new Utilities();
        List<List<Cell>> cellsList = constructFireDirectionLines(currentWorm.weapon.range);
        List<List<Cell>> chosenCells = new ArrayList<List<Cell>>();

        for(List<Cell> cells : cellsList) {
            for(Cell cell : cells) {
                if(utilities.isOccupied(gameState, cell.x, cell.y)){
                    if(cell.occupier.id != enemyWorm.id && cell.occupier.playerId == opponent.id) {
                        chosenCells.add(cells);
                    }
                }
            }
        }

        for(List<Cell> cells : chosenCells){
            for(Cell cell : cells){
                if(utilities.isOccupied(gameState, cell.x, cell.y)){
                    if(cell.occupier.playerId == gameState.myPlayer.id){
                        continue;
                    }else{
                        Direction direction = resolveDirection(currentWorm.position, cell.occupier.position);
                        return new ShootCommand(direction);
                    }
                }
            }
        }

        return EscapeLavaStrategy();
    }


    private Command escapeShootStrategy(){

        Utilities utilities = new Utilities();

        int enemyId = gameState.opponents[0].currentWormId;

        Worm enemyWorm = gameState.opponents[0].worms[enemyId-1];

        int x = currentWorm.position.x;
        int y = currentWorm.position.y;
        int moveX = x;
        int moveY = y;
        boolean conflict = false;

        if(currentWorm.position.x==enemyWorm.position.x){
            moveX = x<16 ? x+1 : x-1;
            moveY = y<16 ? y+1 : y-1;
            conflict = utilities.isPathInvalid(enemyWorm,currentWorm,opponent,moveX,moveY,gameState);

            if(conflict){ //Jika conflict coba jalan lain terlebih dahulu
                moveX = x<16 ? x-1: x+1;
                moveY = y<16 ? y+1 : y-1;
                conflict = utilities.isPathInvalid(enemyWorm,currentWorm,opponent,moveX,moveY,gameState);
                if(conflict){
                    moveX = x<16 ? x+1 : x-1;
                    moveY = y;
                    conflict = utilities.isPathInvalid(enemyWorm,currentWorm,opponent,moveX,moveY,gameState);
                    if(conflict){
                        moveX = x<16 ? x-1 : x+1;
                        moveY = y;
                        conflict = utilities.isPathInvalid(enemyWorm,currentWorm,opponent,moveX,moveY,gameState);
                        if(conflict){
                            moveX = x < 16 ? x+1 : x-1;
                            moveY = y < 16 ? y-1 : y+1;
                            conflict = utilities.isPathInvalid(enemyWorm,currentWorm,opponent,moveX,moveY,gameState);
                            if(conflict){
                                moveX = x < 16 ? x-1 : x+1;
                                moveY = y < 16 ? y-1 : y+1;
                                conflict = utilities.isPathInvalid(enemyWorm,currentWorm,opponent,moveX,moveY,gameState);
                                if(conflict){
                                    return AttackStrategy(enemyWorm);
                                }
                            }
                        }
                    }
                }
            }

        } else if(currentWorm.position.y==enemyWorm.position.y){
            moveY = y<16 ? y+1 : y-1;
            moveX = x<16 ? x+1 : x-1;
            conflict = utilities.isPathInvalid(enemyWorm,currentWorm,opponent,moveX,moveY,gameState);

            if(conflict){ //Jika conflict coba jalan lain terlebih dahulu
                moveY = y<16 ? y-1: y+1;
                moveX = x<16 ? x+1 : x-1;
                conflict = utilities.isPathInvalid(enemyWorm,currentWorm,opponent,moveX,moveY,gameState);
                if(conflict){
                    moveY = y<16 ? y+1 : y-1;
                    moveX = x;
                    conflict = utilities.isPathInvalid(enemyWorm,currentWorm,opponent,moveX,moveY,gameState);
                    if(conflict){
                        moveY = y<16 ? y-1 : y+1;
                        moveX = x;
                        conflict = utilities.isPathInvalid(enemyWorm,currentWorm,opponent,moveX,moveY,gameState);
                        if(conflict){
                            moveY = y < 16 ? y+1 : y-1;
                            moveX = x < 16 ? x-1 : x+1;
                            conflict = utilities.isPathInvalid(enemyWorm,currentWorm,opponent,moveX,moveY,gameState);
                            if(conflict){
                                moveY = y < 16 ? y-1 : y+1;
                                moveX = x < 16 ? y-1 : y+1;
                                conflict = utilities.isPathInvalid(enemyWorm,currentWorm,opponent,moveX,moveY,gameState);
                                if(conflict){
                                    return AttackStrategy(enemyWorm);
                                }
                            }
                        }
                    }
                }
            }
        } else { // currentWorm.position.x != enemyWorm.position.x && currentWorm.position.y != enemyWorm.position.y
            if((currentWorm.position.x > enemyWorm.position.x && currentWorm.position.y < enemyWorm.position.y) || (currentWorm.position.x < enemyWorm.position.x && currentWorm.position.y > enemyWorm.position.y)){
                moveX = x<16 ? x+1 : x-1;
                moveY = y;
                conflict = utilities.isPathInvalid(enemyWorm,currentWorm,opponent,moveX,moveY,gameState);
                if(conflict){
                    moveX = x<16 ? x-1 : x+1;
                    moveY = y;
                    conflict = utilities.isPathInvalid(enemyWorm,currentWorm,opponent,moveX,moveY,gameState);
                    if(conflict){
                        moveY = y<16 ? y+1 : y-1;
                        moveX = x;
                        conflict = utilities.isPathInvalid(enemyWorm,currentWorm,opponent,moveX,moveY,gameState);
                        if(conflict){
                            moveY = y<16 ? y-1 : y+1;
                            moveX = x;
                            conflict = utilities.isPathInvalid(enemyWorm,currentWorm,opponent,moveX,moveY,gameState);
                            if(conflict){
                                moveX = x<16 ? x+1 : x-1;
                                moveY = x<16 ? y-1 : y+1;
                                conflict = utilities.isPathInvalid(enemyWorm,currentWorm,opponent,moveX,moveY,gameState);
                                if(conflict){
                                    moveX = x<16 ? x-1 : x+1;
                                    moveY = x<16 ? y+1 : y-1;
                                    conflict = utilities.isPathInvalid(enemyWorm,currentWorm,opponent,moveX,moveY,gameState);
                                    if(conflict){
                                        return AttackStrategy(enemyWorm);
                                    }
                                }
                            }
                        }
                    }
                }

            } else{
                moveX = x<16 ? x+1 : x-1;
                moveY = y;
                conflict = utilities.isPathInvalid(enemyWorm,currentWorm,opponent,moveX,moveY,gameState);
                if(conflict){
                    moveX = x<16 ? x-1 : x+1;
                    moveY = y;
                    conflict = utilities.isPathInvalid(enemyWorm,currentWorm,opponent,moveX,moveY,gameState);
                    if(conflict){
                        moveY = y<16 ? y+1 : y-1;
                        moveX = x;
                        conflict = utilities.isPathInvalid(enemyWorm,currentWorm,opponent,moveX,moveY,gameState);
                        if(conflict){
                            moveY = y<16 ? y-1 : y+1;
                            moveX = x;
                            conflict = utilities.isPathInvalid(enemyWorm,currentWorm,opponent,moveX,moveY,gameState);
                            if(conflict){
                                moveX = x<16 ? x+1 : x-1;
                                moveY = x<16 ? y+1 : y-1;
                                conflict = utilities.isPathInvalid(enemyWorm,currentWorm,opponent,moveX,moveY,gameState);
                                if(conflict){
                                    moveX = x<16 ? x-1 : x+1;
                                    moveY = x<16 ? y-1 : y+1;
                                    if(conflict){
                                        return AttackStrategy(enemyWorm);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }


        return new MoveCommand(moveX,moveY);
    }

    private Command AttackStrategy(Worm enemyWorm) {
        // First check can use bomb or not
        Direction direction = resolveDirection(currentWorm.position, enemyWorm.position);
        if (currentWorm.id == 2) {
            if (currentWorm.bananaBombs.count > 0) {
                return BananaBombStrategy(enemyWorm, direction);
            }
        } else if (currentWorm.id == 3) {
            if (currentWorm.snowballs.count > 0 && enemyWorm.roundsUntilUnfrozen == 0) {
                return SnowballStrategy(enemyWorm, direction);
            }
        }
        return ShootStrategy(direction,enemyWorm);
    }

    private Command ShootStrategy(Direction direction,Worm enemyWorm){
        Utilities utilities = new Utilities();
        boolean canShoot = true;
        List<List<Cell>> cellsList = constructFireDirectionLines(currentWorm.weapon.range);
        List<Cell> chosenCell = new ArrayList<Cell>();

        for(List<Cell> cells : cellsList) {
            for(Cell cell : cells) {
                if(utilities.isOccupied(gameState, cell.x, cell.y)){
                    if(cell.occupier.id == enemyWorm.id && cell.occupier.playerId == opponent.id) {
                        chosenCell = cells;
                    }
                }
            }
        }


        for(Worm anotherWorm : this.wormsData) {
            for(Cell cell : chosenCell) {
                if(cell.x == anotherWorm.position.x && cell.y == anotherWorm.position.y){
                    canShoot = false;
                    break;
                }
            }
        }

        return canShoot ? new ShootCommand(direction) : AttackAnotherWorm(enemyWorm);
    }

    private Command BananaBombStrategy(Worm enemyWorm, Direction direction){
        for(int i=0;i<5;i++){
            if(i<3){
                for(int j=2-i;j<3+i;j++){
                    int x = enemyWorm.position.x - (2-i);
                    int y = enemyWorm.position.y - (2-j);
                    if((x==gameState.myPlayer.worms[0].position.x && y==gameState.myPlayer.worms[0].position.y) || (x==gameState.myPlayer.worms[2].position.x && y==gameState.myPlayer.worms[2].position.y)){

                        return new ShootCommand(direction);
                    }
                }
            } else{
                for(int j=i-2;j<7-i;j++ ){
                    int x = enemyWorm.position.x - (2-i);
                    int y = enemyWorm.position.y - (2-j);
                    if((x==gameState.myPlayer.worms[0].position.x && y==gameState.myPlayer.worms[0].position.y) || (x==gameState.myPlayer.worms[2].position.x && y==gameState.myPlayer.worms[2].position.y)){
                        return new ShootCommand(direction);
                    }
                }
            }
        }
        return new BombCommand(enemyWorm.position.x, enemyWorm.position.y);
    }

    private Command SnowballStrategy(Worm enemyWorm, Direction direction){
        for(Worm w : gameState.myPlayer.worms){
            int x = w.position.x;
            int y = w.position.y;
            for(int i=enemyWorm.position.x-1;i<=enemyWorm.position.x+1;i++){
                for(int j=enemyWorm.position.y-1;j<=enemyWorm.position.y+1;j++){
                    if(i==x && j==y){
                        return new ShootCommand(direction);
                    }
                }
            }
        }
        return new SnowballCommand(enemyWorm.position.x, enemyWorm.position.y);
    }
}
