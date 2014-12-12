package resources.objects.weapon;

import network.packets.swg.zone.baselines.Baseline.BaselineType;
import resources.network.BaselineBuilder;
import resources.objects.tangible.TangibleObject;
import resources.player.Player;

public class WeaponObject extends TangibleObject {

	private static final long serialVersionUID = 1L;

	private float attackSpeed = 0.5f;
	private float maxRange = 5f;
	private int type = WeaponType.UNARMED;
	
	public WeaponObject(long objectId) {
		super(objectId);
	}

	
	public float getAttackSpeed() {
		return attackSpeed;
	}


	public void setAttackSpeed(float attackSpeed) {
		this.attackSpeed = attackSpeed;
	}


	public float getMaxRange() {
		return maxRange;
	}


	public void setMaxRange(float maxRange) {
		this.maxRange = maxRange;
	}


	public int getType() {
		return type;
	}


	public void setType(int type) {
		this.type = type;
	}


	public void createObject(Player target) {
		super.sendSceneCreateObject(target);
		
		BaselineBuilder bb = new BaselineBuilder(this, BaselineType.WEAO, 3);
		createBaseline3(target, bb);
		bb.sendTo(target);
		
		bb = new BaselineBuilder(this, BaselineType.WEAO, 6);
		createBaseline6(target, bb);
		bb.sendTo(target);
	}

	public void createBaseline3(Player target, BaselineBuilder bb) {
		super.createBaseline3(target, bb);
		
		bb.addFloat(attackSpeed);
		bb.addInt(0); // accuracy (pre-nge)
		bb.addInt(0); // unknown
		bb.addFloat(maxRange);
		bb.addInt(1); // unknown (set to 1 for default weapon)
		bb.addInt(0); // weapon particle effect?
		bb.addInt(0); // weapon particle effect color?
		
		bb.incremeantOperandCount(7);
	}

	public void createBaseline6(Player target, BaselineBuilder bb) {
		super.createBaseline6(target, bb);

		bb.addInt(type);
		
		bb.incremeantOperandCount(1);
	}


	public void createBaseline8(Player target, BaselineBuilder bb) {
		super.createBaseline8(target, bb);
		
	}
	
	public void createBaseline9(Player target, BaselineBuilder bb) {
		super.createBaseline9(target, bb);
		
	}
}