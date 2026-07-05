package fun.kaituo.tagmanager;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class Tag {
    public String permission;
    public String prefix;
    @SerializedName("applied_players")
    public List<String> appliedPlayers;
}
