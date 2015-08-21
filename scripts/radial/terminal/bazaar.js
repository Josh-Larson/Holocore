function getOptions(options) {
	options.add(new RadialOption(RadialItem.ITEM_USE));
	options.add(new RadialOption(RadialItem.EXAMINE));
}

function handleSelection(player, target, selection) {
	switch (selection) {
		case RadialItem.ITEM_USE:
			Log.d("bazaar.js", "Bazaar Selection: ITEM_USE");
			break;
	}
}