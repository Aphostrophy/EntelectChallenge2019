package za.co.entelect.challenge.entities;

import com.google.gson.annotations.SerializedName;

public class SnowBall {
    @SerializedName("freezeDuration")
    public int damage;

    @SerializedName("range")
    public int range;

    @SerializedName("count")
    public int count;

    @SerializedName("freezeRadius")
    public int freezeRadius;
}

//"bananaBombs": { "damage": 20, "range": 5, "count": 3, "damageRadius": 2 },