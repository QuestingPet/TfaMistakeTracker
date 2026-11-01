package com.tfamistaketracker;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class TfaMistakeTrackerPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(TfaMistakeTrackerPlugin.class);
		RuneLite.main(args);
	}
}