package main;

import org.json.simple.JSONObject;

public class Requirement 
{
	private String mLevel;
	private String mBody;
	private String mID;
	private String mJIRAKey;
	
	public Requirement( String aLevel, String aBody, String aID) 
	{
		mLevel = aLevel;
		mBody = aBody;
		mID = aID;
	}
	public void setJIRAKey( String aKey )
	{
		mJIRAKey = aKey;
	}
	public JSONObject toJSON()
	{
		return this.toJSON();
	}
}
