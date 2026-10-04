package net.get900.pixelpirates.world.dungeon;

import net.minecraft.block.*;
import net.minecraft.block.entity.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.World;

/** Server-authoritative persistent controls. No global tick scans, commands, or external state files.
 *  ABYSSAL INSCRIPTION STONE (Phase 5 puzzle sites, authored externally 2026-10-02): the master at design (0,1,5) holds
 *  the progress + Rewarded flag; controls hold design deltas to it (turned by their facing). Drops nothing (the link data
 *  must not travel). Registered from PixelPirates.onInitialize; model/blockstate hand-authored in resources/. */
public final class AbyssPuzzleNodes {
    public static final Identifier ID=new Identifier("pixelpirates","abyss_puzzle_node");
    public static final NodeBlock BLOCK=new NodeBlock();
    public static BlockEntityType<NodeEntity> TYPE;
    private static boolean registered;
    public static void register(){
        if(registered)return;
        Registry.register(Registries.BLOCK,ID,BLOCK);
        Registry.register(Registries.ITEM,ID,new BlockItem(BLOCK,new Item.Settings()));
        TYPE=Registry.register(Registries.BLOCK_ENTITY_TYPE,ID,BlockEntityType.Builder.create(NodeEntity::new,BLOCK).build(null));
        registered=true;
    }
    public static BlockPos turn(BlockPos delta,Direction facing){return switch(facing){
        case EAST -> new BlockPos(-delta.getZ(),delta.getY(),delta.getX());
        case SOUTH -> new BlockPos(-delta.getX(),delta.getY(),-delta.getZ());
        case WEST -> new BlockPos(delta.getZ(),delta.getY(),-delta.getX());
        default -> delta;
    };}
    public static final class NodeBlock extends BlockWithEntity {
        NodeBlock(){super(AbstractBlock.Settings.copy(Blocks.DEEPSLATE).strength(3.5f).dropsNothing().luminance(s->s.get(Properties.LIT)?12:0));setDefaultState(getStateManager().getDefaultState().with(Properties.HORIZONTAL_FACING,Direction.NORTH).with(Properties.LIT,false));}
        protected void appendProperties(StateManager.Builder<Block,BlockState>b){b.add(Properties.HORIZONTAL_FACING,Properties.LIT);}
        public BlockRenderType getRenderType(BlockState s){return BlockRenderType.MODEL;}
        public BlockEntity createBlockEntity(BlockPos p,BlockState s){return new NodeEntity(p,s);}
        public BlockState rotate(BlockState s,BlockRotation r){return s.with(Properties.HORIZONTAL_FACING,r.rotate(s.get(Properties.HORIZONTAL_FACING)));}
        public BlockState mirror(BlockState s,BlockMirror m){return rotate(s,m.getRotation(s.get(Properties.HORIZONTAL_FACING)));}
        public ActionResult onUse(BlockState s,World w,BlockPos pos,PlayerEntity player,Hand hand,BlockHitResult hit){
            if(w.isClient)return ActionResult.SUCCESS;
            if(hand!=Hand.MAIN_HAND)return ActionResult.CONSUME;
            if(player.isSpectator())return ActionResult.PASS;
            if(w.getBlockEntity(pos) instanceof NodeEntity n)n.use((ServerWorld)w,player);
            return ActionResult.CONSUME;
        }
    }
    public static final class NodeEntity extends BlockEntity {
        private int kind=-1,role=-2;
        private BlockPos masterDelta=BlockPos.ORIGIN,rewardDelta=BlockPos.ORIGIN;
        private int[] progress=new int[10];
        private boolean rewarded;
        private long lastInput=Long.MIN_VALUE;
        private String table="";
        public NodeEntity(BlockPos p,BlockState s){super(TYPE,p,s);}
        public void readNbt(NbtCompound n){super.readNbt(n);kind=n.getInt("Kind");role=n.getInt("Role");
            masterDelta=new BlockPos(n.getInt("DX"),n.getInt("DY"),n.getInt("DZ"));
            rewardDelta=new BlockPos(n.getInt("RX"),n.getInt("RY"),n.getInt("RZ"));table=n.getString("RewardTable");
            rewarded=n.getBoolean("Rewarded");progress=n.getIntArray("Progress");
            if(!AbyssPuzzleLogic.valid(kind,progress)){if(kind>=0&&kind<=7)progress=AbyssPuzzleLogic.initial(kind);else kind=-1;}
        }
        protected void writeNbt(NbtCompound n){super.writeNbt(n);n.putInt("Kind",kind);n.putInt("Role",role);
            n.putInt("DX",masterDelta.getX());n.putInt("DY",masterDelta.getY());n.putInt("DZ",masterDelta.getZ());
            n.putInt("RX",rewardDelta.getX());n.putInt("RY",rewardDelta.getY());n.putInt("RZ",rewardDelta.getZ());
            n.putString("RewardTable",table);n.putIntArray("Progress",progress);n.putBoolean("Rewarded",rewarded);
        }
        private void use(ServerWorld w,PlayerEntity player){
            if(kind<0||kind>7){say(player,"This stone is not linked to a puzzle.");return;}
            Direction facing=getCachedState().get(Properties.HORIZONTAL_FACING);
            BlockPos mp=pos.add(turn(masterDelta,facing));
            if(!w.isChunkLoaded(mp)||!(w.getBlockEntity(mp) instanceof NodeEntity master)||master.role!=-1||master.kind!=kind){say(player,"The central inscription is missing or unloaded.");return;}
            if(master.rewarded){say(player,"This puzzle is complete. Its treasure has already been released.");return;}
            if(role==-1){
                if(player.isSneaking()){master.progress=AbyssPuzzleLogic.initial(kind);master.markDirty();master.refreshLights(w);say(player,"Puzzle reset. "+AbyssPuzzleLogic.clue(kind));}
                else say(player,AbyssPuzzleLogic.clue(kind)+" "+AbyssPuzzleLogic.status(kind,master.progress)+" Sneak-use this inscription to reset.");
                return;
            }
            if(role<0||role>=AbyssPuzzleLogic.count(kind))return;
            // Shared per-site debounce prevents accidental double-hand/rapid duplicate input.
            long now=w.getTime();if(master.lastInput!=Long.MIN_VALUE&&now-master.lastInput<4)return;master.lastInput=now;
            if(!AbyssPuzzleLogic.solved(kind,master.progress)){
                AbyssPuzzleLogic.press(kind,master.progress,role);master.markDirty();
                if(kind==0)w.playSound(null,pos,SoundEvents.BLOCK_BELL_USE,SoundCategory.BLOCKS,.8f,.8f+.15f*role);
                master.refreshLights(w);
                say(player,"Stone "+(role+1)+(kind==4?": "+AbyssPuzzleLogic.glyph(role):"")+". "+AbyssPuzzleLogic.status(kind,master.progress));
            }
            if(AbyssPuzzleLogic.solved(kind,master.progress))master.release(w,player);
        }
        private void release(ServerWorld w,PlayerEntity player){
            BlockPos rp=pos.add(turn(rewardDelta,getCachedState().get(Properties.HORIZONTAL_FACING)));
            if(!w.isChunkLoaded(rp)||!(w.getBlockEntity(rp) instanceof ChestBlockEntity chest)){
                say(player,"Solved. Restore the treasure chest on its marked plinth, then touch a control again.");return;
            }
            if(!chest.isEmpty()){say(player,"Solved. Empty the treasure chest, then touch a control again.");return;}
            Identifier loot=Identifier.tryParse(table);if(loot==null){say(player,"The treasure table is not configured.");return;}
            LootableContainerBlockEntity.setLootTable(w,w.random,rp,loot);
            rewarded=true;markDirty();say(player,"The abyssal seal opens. Treasure is now in the marked chest.");
        }
        private void refreshLights(ServerWorld w){
            for(int i=0;i<AbyssPuzzleLogic.count(kind);i++){
                BlockPos p=pos.add(turn(new BlockPos(AbyssPuzzleLogic.nodeX(kind,i),0,-5),getCachedState().get(Properties.HORIZONTAL_FACING)));
                if(!w.isChunkLoaded(p))continue;BlockState s=w.getBlockState(p);if(!s.isOf(BLOCK))continue;
                boolean lit=switch(kind){case 1,2,4 -> (progress[0]&(1<<i))!=0;case 3 -> progress[i]==AbyssPuzzleLogic.DIALS[i];default -> AbyssPuzzleLogic.solved(kind,progress);};
                w.setBlockState(p,s.with(Properties.LIT,lit),Block.NOTIFY_LISTENERS);
            }
        }
        private static void say(PlayerEntity p,String s){p.sendMessage(Text.literal(s),false);}
    }
}
