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
    private ZonesGroup zonesGroup;
    private FaresGroup faresGroup;


    public PtFaresConfigGroup() {
        super(GROUP);
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

    public void setZonesGroup(ZonesGroup zonesGroup) {
        super.addParameterSet(zonesGroup);
        this.zonesGroup = zonesGroup;
    }

    public ZonesGroup getZonesGroup() {
        return zonesGroup;
    }

    public void setFaresGroup(FaresGroup faresGroup) {
        super.addParameterSet(faresGroup);
        this.faresGroup = faresGroup;
    }

    public FaresGroup getFaresGroup() {
        return faresGroup;
    }

    @Override
    public ConfigGroup createParameterSet(String type) {
        if (ZonesGroup.TYPE.equals(type)) {
            return new PtFaresConfigGroup.ZonesGroup();

        } else if (FaresGroup.TYPE.equals(type)) {
            return new PtFaresConfigGroup.FaresGroup();

        } else {
            throw new IllegalArgumentException("Unsupported parameterset-type: " + type);
        }

    }



    public static class ZonesGroup extends ReflectiveConfigGroup{
        public static final String TYPE = "zones";
        private static final String OUT_OF_ZONE_TAG = "outOfZoneTag";

        private static final Set<Zone> zones = new HashSet<>();
        private static String outOfZoneTag = "out";

        public ZonesGroup() {
            super(TYPE);
        }

        @StringGetter(OUT_OF_ZONE_TAG)
        public String getOutOfZoneTag() {
            return outOfZoneTag;
        }

        @StringSetter(OUT_OF_ZONE_TAG)
        public void setOutOfZoneTag(String attributeName) {
            outOfZoneTag = attributeName;
        }

        public void addZone(Zone zone) {
            super.addParameterSet(zone);
            zones.add(zone);
        }

        public Set<Zone> getAllZones(){
            return zones;
        }

        public Set<String> getAllBaseZones(){
            Set<String> allZones = new HashSet<>();
            for(var zone: zones){
                if (!zone.isHybrid){ allZones.add(zone.zoneName); }
            }
            return allZones;
        }

        public Map<String, Set<String>> getAllHybridZones(){
            Map<String, Set<String>> allZones = new HashMap<>();
            for(var zone: zones){
                if (zone.isHybrid){ allZones.put(zone.zoneName, zone.getZoneAssignment()); }
            }
            return allZones;
        }

        @Override
        public ConfigGroup createParameterSet(String type) {
            if (Zone.TYPE.equals(type)) {
                return new PtFaresConfigGroup.ZonesGroup.Zone();
            } else {
                throw new IllegalArgumentException("Unsupported parameterset-type: " + type);
            }

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


    public static class FaresGroup extends ReflectiveConfigGroup{
        public static final String TYPE = "fares";

        private static final String OUT_OF_ZONE_PRICE = "outOfZonePrice";


        private static double outOfZonePrice = 0.;
        private static final Map<Integer, Fare> fares = new HashMap<>();

        public FaresGroup() {
            super(TYPE);
        }



        @StringGetter(OUT_OF_ZONE_PRICE)
        public double getOutOfZonePrice() {
            return outOfZonePrice;
        }

        @StringSetter(OUT_OF_ZONE_PRICE)
        public void setOutOfZonePrice(double price) {
            outOfZonePrice = price;
        }

        public void addFare(Fare fare) {
            super.addParameterSet(fare);
            fares.put(fare.getNumberZones(), fare);
        }

        public Map<Integer, Double> getAllFares(){
            return fares.entrySet().stream()
                    .collect(Collectors.toMap(Map.Entry::getKey,
                            map -> map.getValue().getTicketPrice()));
        }

        public Double getFare(Integer numberZones){
            return fares.get(numberZones).getTicketPrice();
        }

        @Override
        public ConfigGroup createParameterSet(String type) {
            if (FaresGroup.Fare.TYPE.equals(type)) {
                return new PtFaresConfigGroup.FaresGroup.Fare();
            } else {
                throw new IllegalArgumentException("Unsupported parameterset-type: " + type);
            }

        }

        public static class Fare extends ReflectiveConfigGroup{
            public static final String TYPE = "zone";
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
