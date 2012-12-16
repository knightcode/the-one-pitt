package routing.contentpubsub;

import core.Message;

public class NameContentFilter implements ContentFilter
{
	public static final String PUBNAME_PROP = "PubSub-pubname";
	
	private String nameToMatch;
	
	public NameContentFilter(String name)
	{
		nameToMatch = name;
	}
	
	public boolean match(Message m)
	{
		String msgName = (String)m.getProperty(PUBNAME_PROP);
		
		return nameToMatch.equals(msgName);
	}

}
