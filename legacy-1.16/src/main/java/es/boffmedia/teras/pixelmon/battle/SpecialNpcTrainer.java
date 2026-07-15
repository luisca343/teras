package es.boffmedia.teras.pixelmon.battle;

import com.pixelmonmod.pixelmon.battles.api.rules.BattleRules;
import com.pixelmonmod.pixelmon.entities.npcs.NPCTrainer;
import com.pixelmonmod.pixelmon.entities.pixelmon.PixelmonEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import java.util.*;

public class SpecialNpcTrainer extends NPCTrainer {
    private NpcTrainerPartyStorage party;
    private String trainerId;
    private ItemStack[] winnings;
    private boolean startRotationSet;


    public boolean usingDefaultName;
    public boolean usingDefaultGreeting;
    public boolean usingDefaultWin;
    public boolean usingDefaultLose;
    public String greeting;
    public String winMessage;
    public String loseMessage;
    public int winMoney;
    public int pokemonLevel;
    public boolean isGymLeader;
    public boolean canEngage;
    public BattleRules battleRules;



    public SpecialNpcTrainer(EntityType<NPCTrainer> type, World par1World) {
        super(type, par1World);
        this.party = new NpcTrainerPartyStorage(this);
        this.usingDefaultName = true;
        this.usingDefaultGreeting = true;
        this.usingDefaultWin = true;
        this.usingDefaultLose = true;
        this.greeting = "";
        this.winMessage = "";
        this.loseMessage = "";
        this.trainerId = "";
        this.winnings = new ItemStack[0];
        this.startRotationSet = false;
        this.isGymLeader = false;
        this.canEngage = true;
        this.playerEncounters = new HashMap();
        this.winCommands = new ArrayList();
        this.loseCommands = new ArrayList();
        this.forfeitCommands = new ArrayList();
        this.preBattleCommands = new ArrayList();
        this.battleRules = new BattleRules();
    }

    public SpecialNpcTrainer(World world, NpcTrainerPartyStorage party, String trainerId, ItemStack[] winnings, boolean startRotationSet) {
        super(world);
        this.party = party;
        this.trainerId = trainerId;
        this.winnings = winnings;
        this.startRotationSet = startRotationSet;
    }

    public SpecialNpcTrainer(World par1World) {
        super(par1World);
        this.party = new NpcTrainerPartyStorage(this);
        this.usingDefaultName = true;
        this.usingDefaultGreeting = true;
        this.usingDefaultWin = true;
        this.usingDefaultLose = true;
        this.greeting = "";
        this.winMessage = "";
        this.loseMessage = "";
        this.trainerId = "";
        this.winnings = new ItemStack[0];
        this.startRotationSet = false;
        this.isGymLeader = false;
        this.canEngage = true;
        this.playerEncounters = new HashMap();
        this.winCommands = new ArrayList();
        this.loseCommands = new ArrayList();
        this.forfeitCommands = new ArrayList();
        this.preBattleCommands = new ArrayList();
        this.battleRules = new BattleRules();
    }

    @Override
    public NpcTrainerPartyStorage getPokemonStorage() {
        return this.party;
    }

    public PixelmonEntity releasePokemon(UUID newPokemonUUID) {
        return this.party.find(newPokemonUUID).getOrSpawnPixelmon(this);
    }
}
