package routing.contentpubsub;

import core.*;

public interface ContentFilter
{
	public boolean match(Message m);
}
