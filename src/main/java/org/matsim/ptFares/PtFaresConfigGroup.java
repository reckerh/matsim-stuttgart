package org.matsim.ptFares;

import org.matsim.core.config.ReflectiveConfigGroup;

public class PtFaresConfigGroup extends ReflectiveConfigGroup {

    public static final String GROUP_NAME = "ptFares";

    private static final String PT_FARE_ZONE_ATTRIBUTE_NAME = "ptFareZoneAttributeName";


    public PtFaresConfigGroup() {
        super(GROUP_NAME);
    }

    private String ptFareZoneAttributeName = "ptFareZone";


    @StringGetter(PT_FARE_ZONE_ATTRIBUTE_NAME)
    public String getPtFareZoneAttributeName() {
        return ptFareZoneAttributeName;
    }

    @StringSetter(PT_FARE_ZONE_ATTRIBUTE_NAME)
    public void setPtFareZoneAttributeName(String attributeName){
        this.ptFareZoneAttributeName = attributeName;
    }




}
