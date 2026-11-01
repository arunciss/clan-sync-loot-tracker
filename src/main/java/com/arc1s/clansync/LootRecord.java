package com.arc1s.clansync;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class LootRecord
{
	private String uid;
	private String player;
	private String recordedAt;
	private LootSourceType type;
	private String source;
	private Integer combatLevel;
	private int amount = 1;
	private Integer killCount;
	private Integer containerItemId;
	private String parentUid;
	private List<LootItem> items = new ArrayList<>();
}
