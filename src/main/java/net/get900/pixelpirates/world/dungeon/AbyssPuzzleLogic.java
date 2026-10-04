package net.get900.pixelpirates.world.dungeon;

import java.util.Arrays;

/** Pure deterministic puzzle rules, shared by server interaction and tests. */
public final class AbyssPuzzleLogic {
    public static final int[] SEQUENCE={0,2,1,3,2};
    public static final int[] WEIGHTS={1,3,7,12};
    public static final int[] DIALS={1,3,2,1};
    public static final int[] PAIRS={0,2,1,3,2,0,3,1};
    private AbyssPuzzleLogic() {}
    public static int count(int kind) { return switch(kind) { case 0,2,3,5,6 -> 4; case 7 -> 6; case 1 -> 5; case 4 -> 8; default -> throw new IllegalArgumentException("Unknown puzzle"); }; }
    public static int[] initial(int kind) { count(kind);int[] s=new int[10];if(kind==1)s[0]=21;if(kind==4)s[1]=-1;if(kind==6){s[0]=3;s[1]=1;s[2]=4;s[3]=2;}return s; }
    public static int nodeX(int kind,int node){return kind==7?-15+6*node:kind==4?-14+4*node:kind==1?-12+6*node:-12+8*node;}
    public static void press(int kind,int[] s,int node) {
        if(s.length!=10||node<0||node>=count(kind))throw new IllegalArgumentException("Invalid puzzle input");
        if(solved(kind,s))return;
        switch(kind) {
            case 5 -> s[0]=node+1;
            case 6 -> {
                if(node<3){int v=s[node];s[node]=s[node+1];s[node+1]=v;s[4]=0;}
                else s[4]=(s[0]==1&&s[1]==2&&s[2]==3&&s[3]==4)?1:0;
            }
            case 7 -> {
                switch(node){
                    case 0 -> s[0]=3;case 1 -> s[1]=5;
                    case 2 -> {int n=Math.min(s[0],5-s[1]);s[0]-=n;s[1]+=n;}
                    case 3 -> {int n=Math.min(s[1],3-s[0]);s[1]-=n;s[0]+=n;}
                    case 4 -> s[0]=0;case 5 -> s[1]=0;
                }
            }
            case 0 -> s[0]=node==SEQUENCE[s[0]]?s[0]+1:(node==SEQUENCE[0]?1:0);
            case 1 -> { for(int i=Math.max(0,node-1);i<=Math.min(4,node+1);i++)s[0]^=1<<i; }
            case 2 -> s[0]^=1<<node;
            case 3 -> s[node]=(s[node]+1)%4;
            case 4 -> {
                if((s[0]&(1<<node))!=0)return;
                if(s[1]<0)s[1]=node;
                else if(s[1]!=node){if(PAIRS[s[1]]==PAIRS[node])s[0]|=(1<<s[1])|(1<<node);s[1]=-1;}
            }
        }
    }
    public static boolean solved(int kind,int[] s) {
        return switch(kind) {
            case 5 -> s[0]==1;
            case 6 -> s[4]==1&&s[0]==1&&s[1]==2&&s[2]==3&&s[3]==4;
            case 7 -> s[1]==4;
            case 0 -> s[0]==SEQUENCE.length;
            case 1 -> s[0]==0;
            case 2 -> weight(s)==10;
            case 3 -> s[0]==1&&s[1]==3&&s[2]==2&&s[3]==1;
            case 4 -> s[0]==255;
            default -> false;
        };
    }
    public static int weight(int[] s){int n=0;for(int i=0;i<4;i++)if((s[0]&(1<<i))!=0)n+=WEIGHTS[i];return n;}
    public static boolean valid(int kind,int[] s) {
        if(kind<0||kind>7||s.length!=10)return false;
        return switch(kind){case 5 -> s[0]>=0&&s[0]<=4;case 6 -> Arrays.stream(s,0,4).allMatch(v->v>=1&&v<=4)&&Arrays.stream(s,0,4).distinct().count()==4&&(s[4]==0||s[4]==1);case 7 -> s[0]>=0&&s[0]<=3&&s[1]>=0&&s[1]<=5;case 0 -> s[0]>=0&&s[0]<=5;case 1 -> s[0]>=0&&s[0]<32;case 2 -> s[0]>=0&&s[0]<16;case 3 -> Arrays.stream(s,0,4).allMatch(v->v>=0&&v<4);case 4 -> s[0]>=0&&s[0]<256&&s[1]>=-1&&s[1]<8;default -> false;};
    }
    public static String clue(int kind) {return switch(kind){
        case 5 -> "Exactly ONE witness tells the truth. Witness I: seal I or IV. Witness II: seal II or IV. Witness III: seal II. Witness IV: seal IV. Select the seal making exactly one statement true.";
        case 6 -> "Restore the procession: Dawn(1), Noon(2), Dusk(3), Night(4). Controls I/II/III swap adjacent pairs 1-2 / 2-3 / 3-4. Control IV submits the order.";
        case 7 -> "Measure FOUR in the large vessel. Capacity: small 3, large 5. I fill small; II fill large; III pour small into large; IV pour large into small; V empty small; VI empty large. Pour until the source empties or receiver fills.";
        case 0 -> "Bell memorial: I, III, II, IV, III. Wrong notes restart the song; I begins it again.";
        case 1 -> "Each lantern switch flips itself and its immediate neighbours. Extinguish all five lights.";
        case 2 -> "The ferryman demands exactly ten. Weights I-IV are 1, 3, 7 and 12. Toggle weights onto the scale.";
        case 3 -> "Read the tide tablets I-IV: Dawn, Dusk, Zenith, Dawn. Each dial cycles North, Dawn, Zenith, Dusk.";
        case 4 -> "Find four matching pairs among eight rune stones. Matching pairs remain bound; mistakes are harmless.";
        default -> "Unknown inscription";};}
    public static String status(int kind,int[] s){return switch(kind){
        case 5 -> "Selected seal: "+(s[0]==0?"none":Integer.toString(s[0]))+". Test the four statements against that choice.";
        case 6 -> "Procession: "+s[0]+", "+s[1]+", "+s[2]+", "+s[3]+". IV submits; an incorrect submission changes nothing.";
        case 7 -> "Small: "+s[0]+"/3; large: "+s[1]+"/5. Goal: 4 in large.";
        case 0 -> "Notes remembered: "+s[0]+"/5";
        case 1 -> "Lights I-V: "+bits(s[0],5);
        case 2 -> "Weight: "+weight(s)+"/10; selected I-IV: "+bits(s[0],4);
        case 3 -> "Dials I-IV: "+s[0]+", "+s[1]+", "+s[2]+", "+s[3]+" (0 North, 1 Dawn, 2 Zenith, 3 Dusk)";
        case 4 -> "Bound I-VIII: "+bits(s[0],8)+(s[1]>=0?"; revealed stone "+(s[1]+1)+": "+glyph(s[1]):"");
        default -> "";};}
    public static String glyph(int node){return new String[]{"Anchor","Eye","Crown","Wave"}[PAIRS[node]];}
    private static String bits(int mask,int count){StringBuilder b=new StringBuilder();for(int i=0;i<count;i++)b.append((mask&(1<<i))!=0?"x ":"o ");return b.toString().trim();}
}
