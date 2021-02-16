package za.co.entelect.challenge.command;

import za.co.entelect.challenge.command.*;
import za.co.entelect.challenge.entities.*;
import za.co.entelect.challenge.enums.CellType;
import za.co.entelect.challenge.enums.Direction;

import java.util.*;
import java.util.stream.Collectors;
import java.lang.*;


public class Utilities {

    public Utilities() {

    }

    public int getDirtID(List<Cell> surroundingBlocks){
        for(int i=0;i<surroundingBlocks.size();i++){
            if(surroundingBlocks.get(i).type==CellType.DIRT){
                return i;
            }
        }
        return -1;
    }

    public float gradient(Worm w1, Worm w2) {
        float deltaX = Math.abs(w1.position.x - w2.position.x);
        float deltaY = Math.abs(w1.position.y - w2.position.y);
        return deltaY/deltaX;
    }

    public boolean isPathInvalid(Worm enemyWorm, Worm currentWorm,Opponent opponent, int moveX, int moveY, GameState gameState){
        int y = currentWorm.position.y;
        int x = currentWorm.position.x;

        for (Worm cacingMusuh : opponent.worms) {
            if ((cacingMusuh.position.x == moveX  && cacingMusuh.position.y ==moveY &&cacingMusuh.health>0) || (gameState.myPlayer.worms[0].position.x==moveX && gameState.myPlayer.worms[0].position.y==moveY) || (gameState.myPlayer.worms[1].position.x==moveX && gameState.myPlayer.worms[1].position.y==moveY)|| (gameState.myPlayer.worms[2].position.x==moveX && gameState.myPlayer.worms[2].position.y==moveY)){
                return true;
            }
        }
        if(gameState.map[moveY][moveX].type == CellType.DEEP_SPACE || gameState.map[moveY][moveX].type == CellType.DIRT){
            return true;
        }
        return false;
    }

}

