package es.boffmedia.teras.pixelmon.battle.handlers;

import com.pixelmonmod.pixelmon.api.battles.AttackCategory;
import com.pixelmonmod.pixelmon.battles.attacks.Attack;
import com.pixelmonmod.pixelmon.battles.controller.log.AttackResult;
import com.pixelmonmod.pixelmon.battles.controller.log.MoveResults;
import com.pixelmonmod.pixelmon.battles.controller.log.action.type.AttackAction;
import com.pixelmonmod.pixelmon.battles.controller.participants.PixelmonWrapper;
import es.boffmedia.teras.pixelmon.battle.TerasBattle;

import java.util.Arrays;

import static es.boffmedia.teras.pixelmon.battle.TerasBattleLog.*;

public class AttackActionHandler implements BattleActionHandler<AttackAction> {
    @Override
    public void handle(AttackAction action, TerasBattle terasBattle) {
        PixelmonWrapper pokemon = (PixelmonWrapper) getProtectedProperty("pokemon", action);
        Attack attack = (Attack) getProtectedProperty("attack", action);
        boolean wildPokemon = (boolean) getProtectedProperty("wildPokemon", action);
        String pokemonName = (String) getProtectedProperty("pokemonName", action);
        String[] targets = (String[]) getProtectedProperty("targets", action);
        MoveResults[] moveResults = (MoveResults[]) getProtectedProperty("moveResults", action);

        String move = attack.getActualMove().getAttackName();
        String source = terasBattle.getPositionString(pokemon);

        String attackStr =  "|move|" + getPositionAndNameString(pokemon, terasBattle) + "|" + move + "|";

        PixelmonWrapper moveTarget = pokemon;
        for (MoveResults result : moveResults) {
            if (result.target != null) {
                moveTarget = result.target;
                break;
            }
        }

        String mainTargetStr = getPositionAndNameString(moveTarget, terasBattle);
        attackStr += mainTargetStr + "|";

        if(moveResults.length > 1){
            attackStr += "[spread] ";
            attackStr += Arrays.stream(moveResults).map((target) -> terasBattle.getPositionString(target.target)).reduce((a, b) -> a + "," + b).get();
        }

        appendLine(terasBattle, attackStr);

        if(attack.getAttackCategory().equals(AttackCategory.STATUS)){
            handleStatusMove(move, attack, pokemon, terasBattle);
            return;
        }
        
        for (MoveResults moveResult : moveResults) {
            PixelmonWrapper target = moveResult.target;
            String targetName = target.getNickname();

            int currentHealth = moveResult.getTarget().getHealth();
            int maxHealth = moveResult.getTarget().getMaxHealth();

            if(currentHealth == 0){
                appendLine(terasBattle, "|-damage|" + terasBattle.getPositionString(target) + ": " + targetName + "|0 fnt");
            }else{
                appendLine(terasBattle, "|-damage|" + terasBattle.getPositionString(target) + ": " + targetName + "|" + currentHealth + "\\/" + maxHealth);
            }
        }

        // After faint: |faint|p2b: Wugtrio
        for (MoveResults moveResult : moveResults) {
            PixelmonWrapper target = moveResult.target;
            if(moveResult.getTarget().getHealth() == 0){
                appendLine(terasBattle, "|faint|" + terasBattle.getPositionString(target) + ": " + target.getNickname());
            }
        }
    }
    
    private void handleStatusMove(String move, Attack attack, PixelmonWrapper pokemon, TerasBattle terasBattle) {
        switch (move) {
            case "Wonder Room": case "Trick Room": case "Magic Room":
                appendLine(terasBattle, "|-fieldstart|"+move+"|[of] " + getPositionAndNameString(pokemon, terasBattle));
                break;
            case "Protect": case "Detect": case "Endure": case "King's Shield": case "Spiky Shield": 
            case "Baneful Bunker": case "Obstruct": case "Quick Guard": case "Wide Guard": case "Crafty Shield": 
            case "Mat Block": case "Feint": case "Follow Me": case "Rage Powder": case "Ally Switch": 
            case "Helping Hand": case "Tailwind": case "Lucky Chant": case "Safeguard": case "Mist": 
            case "Light Screen": case "Reflect": case "Aurora Veil": case "Brick Break": case "Defog": 
            case "Haze": case "Heal Bell": case "Heal Pulse": case "Memento":
                if(!attack.moveResult.getResult().equals(AttackResult.failed)) 
                    appendLine(terasBattle, "|-activate|" + getPositionAndNameString(pokemon, terasBattle) + "|move: " + move);
                break;
            default:
                appendLine(terasBattle, "|-activate|" + getPositionAndNameString(pokemon, terasBattle) + "|move: " + move);
                break;
        }
    }
}