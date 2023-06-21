package org.matsim.stuttgart.analysis;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;

import java.util.HashMap;

public class CountingStation {

    public String name;
    //public int count;
    //public HashSet<Id<Link>> surveyedLinks;
    public HashMap<Id<Link>, Integer> bikeCounts;

    public CountingStation(String name, String[] surveyedLinks) {
        this.name = name;
        this.bikeCounts = new HashMap<>();
        for (String linkId : surveyedLinks) {
            this.bikeCounts.put(Id.createLinkId(linkId), 0);
        }
    }

}
