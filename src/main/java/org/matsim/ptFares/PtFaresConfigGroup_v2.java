package org.matsim.ptFares;
import org.matsim.core.config.ConfigGroup;
import org.matsim.core.config.ReflectiveConfigGroup;
import org.matsim.core.utils.collections.CollectionUtils;
import java.util.HashSet;
import java.util.Set;

/**
 * @author dwedekind
 */

public class PtFaresConfigGroup_v2 extends ReflectiveConfigGroup {

    public static final String GROUP_NAME = "ptFares";
    private static final String PT_FARE_ZONE_ATTRIBUTE_NAME = "ptFareZoneAttributeName";
    private static final String OUT_OF_ZONE_TAG = "outOfZoneTag";


    private String ptFareZoneAttributeName = "ptFareZone";
    private String outOfZoneTag = "out";


    public PtFaresConfigGroup_v2() {
        super(GROUP_NAME);
    }


    @StringGetter(PT_FARE_ZONE_ATTRIBUTE_NAME)
    public String getPtFareZoneAttributeName() {
        return ptFareZoneAttributeName;
    }


    @StringSetter(PT_FARE_ZONE_ATTRIBUTE_NAME)
    public void setPtFareZoneAttributeName(String attributeName) {
        this.ptFareZoneAttributeName = attributeName;
    }


    @StringGetter(OUT_OF_ZONE_TAG)
    public String getOutOfZoneTag() {
        return outOfZoneTag;
    }


    @StringSetter(OUT_OF_ZONE_TAG)
    public void setOutOfZoneTag(String attributeName) {
        this.outOfZoneTag = attributeName;
    }


    @Override
    public ConfigGroup createParameterSet(String type) {
        if (ZonesParameterSet.TYPE.equals(type)) {
            return new ZonesParameterSet();
        } else {
            throw new IllegalArgumentException("Unsupported parameterset-type: " + type);
        }
    }



    @Override
    public void addParameterSet(ConfigGroup set) {
        if (set instanceof ZonesParameterSet) {
            addZonesSettings((ZonesParameterSet) set);
        } else {
            throw new IllegalArgumentException("Unsupported parameterset: " + set.getClass().getName());
        }
    }


    public void addZonesSettings(ZonesParameterSet settings) {
        super.addParameterSet(settings);
    }


    public static class ZonesParameterSet extends ReflectiveConfigGroup {

        private static final String TYPE = "zones";
        private final ZonesParameterSet zones = new ZonesParameterSet();
/*        private final Set<ZoneParameterSet> zoneSettings = new HashSet<>();*/

        private static final String TEST = "test";
        private String test = "test";


        public ZonesParameterSet() {
            super(TYPE);
        }

/*        public void addZonesSettings(ZoneParameterSet settings) {
            this.zoneSettings.add(settings);
        }


        public Set<ZoneParameterSet> getZoneTypes() {
            return this.zoneSettings;
        }


        public static class ZoneParameterSet extends ReflectiveConfigGroup {

            public ZoneParameterSet(){ super(TYPE);}

            private static final String TYPE = "zone";

            private static final String ZONE_NAME = "zoneName";
            private static final String IS_HYBRID = "isHybrid";
            private static final String ZONE_ASSIGNMENT = "zoneAssignment";

            private String zoneName;
            private Boolean isHybrid;
            private Set<String> zoneAssignment;

            @StringGetter(ZONE_NAME)
            public String getZoneName(){
                return zoneName;
            }

            @StringSetter(ZONE_NAME)
            public void setZoneName(String zoneName){
                this.zoneName = zoneName;
            }

            @StringGetter(IS_HYBRID)
            public Boolean getIsHybrid(){
                return isHybrid;
            }

            @StringSetter(IS_HYBRID)
            public void setIsHybrid(Boolean isHybrid){
                this.isHybrid = isHybrid;
            }

            @StringGetter(ZONE_ASSIGNMENT)
            private String getDeterministicZoneAssignment() {
                return CollectionUtils.setToString(this.zoneAssignment);
            }

            public Set<String> getZoneAssignment() {
                return this.zoneAssignment;
            }

            @StringSetter(ZONE_ASSIGNMENT)
            private void setZoneAssignment(String modes) {
                setZoneAssignment(CollectionUtils.stringToSet(modes));
            }

            public void setZoneAssignment(Set<String> modes) {
                this.zoneAssignment.clear();
                this.zoneAssignment.addAll(modes);
            }

        }*/


    }


}
