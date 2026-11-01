package com.arc1s.clansync;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class LootItem
{
	private int id;
	private int quantity;
	private Integer price;

	public LootItem(int id, int quantity)
	{
		this.id = id;
		this.quantity = quantity;
	}
}
