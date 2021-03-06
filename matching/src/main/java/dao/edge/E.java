package dao.edge;

import dao.vertex.V;

import java.util.Arrays;

import static com.google.common.base.Preconditions.checkArgument;

public class E {
    public enum Type {
        RID_REF(-1, 0),
        CLS_REF(-1, 0),
        REF_REF(0, 0),
        REF_TKN(0, 1),
        TKN_SIM(1, 2),
        TKN_NCK(1, 2),
        SIM_ABR(2, 3);

        private int inLevel;
        private int outLevel;
        Type(int inLevel, int outLevel) {
            this.inLevel = inLevel;
            this.outLevel = outLevel;
        }

        /**
         * Gets is edge type is inter level or not?
         * For example SEQ type is not interlevel.
         *
         * @return  boolean value of isInterLevel
         */
        public boolean isInterLevel() {return inLevel != outLevel;}

        /**
         * Gets the level of source vertex
         *
         * @return int value of source vertex level
         */
        public int getInLevel() {
            return inLevel;
        }

        /**
         * Gets the level of destination vertex
         *
         * @return int value of destination vertex level
         */
        public int getOutLevel() {
            return outLevel;
        }

        public static Type getTypeByLevels(int inLevel, int outLevel){
            checkArgument(inLevel >= 0 && inLevel <= 2, "InLevel argument was %s but expected in range [0, 2].", inLevel);
            checkArgument(inLevel + 1 == outLevel, "OutLevel should be InLevel + 1.");
            return Arrays.stream(Type.values()).filter(t -> t.inLevel == inLevel && t.outLevel == outLevel).findAny().orElse(null);
        }
    }

    //region Fields
    private V inV;
    private V outV;
    private Type type;
    private Float weight;
    //endregion


    //region Getters & Setters
    /**
     * Gets from vertex of this directed edge.
     *
     * @return from vertex
     */
    public V getInV() {
        return inV;
    }

    public void setInV(V inV) {
        this.inV = inV;
    }

    /**
     * Gets to vertex of this directed edge
     *
     * @return to vertex
     */
    public V getOutV() {
        return outV;
    }

    public void setOutV(V outV) {
        this.outV = outV;
    }

    /**
     * Gets type of the edge
     *
     * @return enum value of type
     */
    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    /**
     * Gets edge weight
     *
     * @return numeric value of weight
     */
    public Float getWeight() {
        return weight;
    }

    public void setWeight(Float weight) {
        this.weight = weight;
    }
    //endregion


    //region Constructors
    public E(V inV, V outV, Type type, Float weight) {
        this.inV = inV;
        this.outV = outV;
        this.type = type;
        this.weight = weight;
    }

    public E(V inV, V outV, String type, String weight) {
        this(inV, outV, Type.valueOf(type), Float.valueOf(weight));
    }
    //endregion

    @Override
    public String toString() {
        return String.format("E[%s] %s --> %s", type, inV.getVal(), outV.getVal());
    }
}
