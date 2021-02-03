package org.matsim.stuttgart.ptFares;

import org.matsim.core.config.ConfigGroup;
import org.matsim.core.config.ReflectiveConfigGroup;
import org.matsim.core.utils.collections.CollectionUtils;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author dwedekind
 */

public class PtFaresConfigGroup extends ReflectiveConfigGroup {
    public static final String GROUP = "ptFares";

    private static final String PT_FARE_ZONE_ATTRIBUTE_NAME = "ptFareZoneAttributeName";
    private static final String PT_INTERACTION_PREFIX = "ptInteractionPrefix";

    private static String ptFareZoneAttributeName = "ptFareZone";
    private static String ptInteractionPrefix = "pt interaction";

    private PtFaresConfigGroup.ZonesGroup zonesGroup;
    private PtFaresConfigGroup.FaresGroup faresGroup;

    public PtFaresConfigGroup() {
        super(GROUP);
    }

    @Override
    public ConfigGroup createParameterSet(String type) {
        if (PtFaresConfigGroup.ZonesGroup.TYPE.equals(type)) {
            return new PtFaresConfigGroup.ZonesGroup();
        } else if (PtFaresConfigGroup.FaresGroup.TYPE.equals(type)) {
            return new PtFaresConfigGroup.FaresGroup();
        } else {
            throw new IllegalArgumentException("Unsupported parameterset-type: " + type);
        }
    }

    public void addParameterSet(ConfigGroup cfg) {
        if (cfg instanceof PtFaresConfigGroup.ZonesGroup) {
            this.setZonesGroup((ZonesGroup)cfg);
        } else if (cfg instanceof PtFaresConfigGroup.FaresGroup) {
            this.setFaresGroup((FaresGroup) cfg);
        } else {
            throw new IllegalArgumentException("Unsupported parameterset: " + cfg.getClass().getName());
        }
    }

    public PtFaresConfigGroup.ZonesGroup setZonesGroup(PtFaresConfigGroup.ZonesGroup cfg) {
        PtFaresConfigGroup.ZonesGroup old = getZonesGroup();
        this.zonesGroup = cfg;
        super.addParameterSet(cfg);
        return old;
    }

    public PtFaresConfigGroup.FaresGroup setFaresGroup(PtFaresConfigGroup.FaresGroup cfg) {
        PtFaresConfigGroup.FaresGroup old = getFaresGroup();
        this.faresGroup = cfg;
        super.addParameterSet(cfg);
        return old;
    }

    public PtFaresConfigGroup.ZonesGroup getZonesGroup() {
        return this.zonesGroup;
    }

    public PtFaresConfigGroup.FaresGroup getFaresGroup() {
        return this.faresGroup;
    }

    @StringGetter(PT_FARE_ZONE_ATTRIBUTE_NAME)
    public String getPtFareZoneAttributeName() {
        return ptFareZoneAttributeName;
    }

    @StringSetter(PT_FARE_ZONE_ATTRIBUTE_NAME)
    public void setPtFareZoneAttributeName(String attributeName) {
        ptFareZoneAttributeName = attributeName;
    }

    @StringGetter(PT_INTERACTION_PREFIX)
    public String getPtInteractionPrefix() {
        return ptInteractionPrefix;
    }

    @StringSetter(PT_INTERACTION_PREFIX)
    public void setPtInteractionPrefix(String attributeName) {
        ptInteractionPrefix = attributeName;
    }


    public static class ZonesGroup extends ReflectiveConfigGroup {
        public static final String TYPE = "zones";
        private static final String OUT_OF_ZONE_TAG = "outOfZoneTag";

        private static String outOfZoneTag = "out";
        private static final Set<PtFaresConfigGroup.ZonesGroup.Zone> zones = new HashSet<>();

        public ZonesGroup() {
            super(TYPE);
        }

        @Override
        public ConfigGroup createParameterSet(String type) {
            if (Zone.TYPE.equals(type)) {
                return new PtFaresConfigGroup.ZonesGroup.Zone();
            } else {
                throw new IllegalArgumentException("Unsupported parameterset-type: " + type);
            }
        }

        public void addParameterSet(ConfigGroup cfg) {
            if (cfg instanceof PtFaresConfigGroup.ZonesGroup.Zone) {
                this.addZone((ZonesGroup.Zone)cfg);
            } else {
                throw new IllegalArgumentException("Unsupported parameterset: " + cfg.getClass().getName());
            }
        }

        public void addZone(PtFaresConfigGroup.ZonesGroup.Zone zone) {
            super.addParameterSet(zone);
            zones.add(zone);
        }

        public Set<PtFaresConfigGroup.ZonesGroup.Zone> getZones(){
            return zones;
        }

        public Set<Zone> getBaseZones(){
            return zones.stream()
                    .filter(zones -> !zones.isHybrid)
                    .collect(Collectors.toSet());
        }

        public Set<String> getBaseZoneStrings(){
            return getBaseZones().stream()
                    .map(Zone::getZoneName)
                    .collect(Collectors.toSet());
        }

        public Set<Zone> getHybridZones(){
            return zones.stream()
                    .filter(zones -> zones.isHybrid)
                    .collect(Collectors.toSet());
        }

        public Map<String, Set<String>> getHybridZoneStringsWithCorrBaseZones(){
            return getHybridZones().stream()
                    .collect(Collectors.toMap(
                            Zone::getZoneName,
                            Zone::getZoneAssignment)
                    );
        }

        @StringGetter(OUT_OF_ZONE_TAG)
        public String getOutOfZoneTag() {
            return outOfZoneTag;
        }

        @StringSetter(OUT_OF_ZONE_TAG)
        public void setOutOfZoneTag(String attributeName) {
            outOfZoneTag = attributeName;
        }

        public static class Zone extends ReflectiveConfigGroup{
            public static final String TYPE = "zone";

            public Zone() {
                super(TYPE);
            }

            public Zone(String zoneName, boolean isHybrid) {
                super(TYPE);
                setZoneName(zoneName);
                setIsHybrid(isHybrid);
            }

            public Zone(String zoneName, boolean isHybrid, Set<String> zoneAssignment) {
                super(TYPE);
                setZoneName(zoneName);
                setIsHybrid(isHybrid);
                setZoneAssignment(zoneAssignment);
            }

            private static final String ZONE_NAME = "zoneName";
            private static final String IS_HYBRID = "isHybrid";
            private static final String ZONE_ASSIGNMENT = "zoneAssignment";

            private String zoneName;
            private boolean isHybrid;
            private final Set<String> zoneAssignment = new HashSet<>();

            @StringGetter(ZONE_NAME)
            public String getZoneName(){
                return zoneName;
            }

            @StringSetter(ZONE_NAME)
            public void setZoneName(String zoneName){
                this.zoneName = zoneName;
            }

            @StringGetter(IS_HYBRID)
            public boolean getIsHybrid(){
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

        }

    }


    public static class FaresGroup extends ReflectiveConfigGroup {
        public static final String TYPE = "fares";
        private static final String OUT_OF_ZONE_PRICE = "outOfZonePrice";

        private static final Map<Integer, PtFaresConfigGroup.FaresGroup.Fare> fares = new HashMap<>();
        private static double outOfZonePrice = 0.;

        public FaresGroup() {
            super(TYPE);
        }

        @Override
        public ConfigGroup createParameterSet(String type) {
            if (FaresGroup.Fare.TYPE.equals(type)) {
                return new PtFaresConfigGroup.FaresGroup.Fare();
            } else {
                throw new IllegalArgumentException("Unsupported parameterset-type: " + type);
            }
        }

        public void addParameterSet(ConfigGroup cfg) {
            if (cfg instanceof PtFaresConfigGroup.FaresGroup.Fare) {
                this.addFare((FaresGroup.Fare)cfg);
            } else {
                throw new IllegalArgumentException("Unsupported parameterset: " + cfg.getClass().getName());
            }
        }

        public void addFare(PtFaresConfigGroup.FaresGroup.Fare fare) {
            super.addParameterSet(fare);
            fares.put(fare.getNumberZones(), fare);
        }

        public Map<Integer, PtFaresConfigGroup.FaresGroup.Fare> getFares(){
            return fares;
        }

        public Double getFare(Integer numberZones){
            return fares.get(numberZones).getTicketPrice();
        }

        public Map<Integer, Double> getFaresAsMap(){
            return fares.entrySet().stream()
                    .collect(Collectors.toMap(Map.Entry::getKey,
                            map -> map.getValue().getTicketPrice()));
        }

        @StringGetter(OUT_OF_ZONE_PRICE)
        public double getOutOfZonePrice() {
            return outOfZonePrice;
        }

        @StringSetter(OUT_OF_ZONE_PRICE)
        public void setOutOfZonePrice(double price) {
            outOfZonePrice = price;
        }

        public static class Fare extends ReflectiveConfigGroup{
            public static final String TYPE = "fare";
            private static final String NUMBER_ZONES = "numberZones";
            private static final String TICKET_PRICE = "ticketPrice";

            private int numberZones = 0;
            private double ticketPrice = 0.;

            public Fare() {
                super(TYPE);
            }

            public Fare(int numberZones, double ticketPrice) {
                super(TYPE);
                setNumberZones(numberZones);
                setTicketPrice(ticketPrice);
            }

            @StringGetter(NUMBER_ZONES)
            public Integer getNumberZones(){
                return numberZones;
            }

            @StringSetter(NUMBER_ZONES)
            public void setNumberZones(int numberZones){
                this.numberZones = numberZones;
            }

            @StringGetter(TICKET_PRICE)
            public Double getTicketPrice(){
                return ticketPrice;
            }

            @StringSetter(TICKET_PRICE)
            public void setTicketPrice(double ticketPrice){
                this.ticketPrice = ticketPrice;
            }

        }

    }

}
