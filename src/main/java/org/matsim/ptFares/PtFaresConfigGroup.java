package org.matsim.ptFares;

import org.matsim.core.config.ConfigGroup;
import org.matsim.core.config.ReflectiveConfigGroup;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PtFaresConfigGroup extends ReflectiveConfigGroup {

    public static final String GROUP_NAME = "ptFares";
    private static final String PT_FARE_ZONE_ATTRIBUTE_NAME = "ptFareZoneAttributeName";
    private String ptFareZoneAttributeName = "ptFareZone";

    private final List<ZonePricesParameterSet> zonePriceSettings = new ArrayList<>();


    public PtFaresConfigGroup() {
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


    @Override
    public ConfigGroup createParameterSet(String type) {
        if (ZonePricesParameterSet.TYPE.equals(type)) {
            return new ZonePricesParameterSet();
        } else {
            throw new IllegalArgumentException("Unsupported parameterset-type: " + type);
        }
    }


    @Override
    public void addParameterSet(ConfigGroup set) {
        if (set instanceof ZonePricesParameterSet) {
            addZonePriceSettings((ZonePricesParameterSet) set);
        } else {
            throw new IllegalArgumentException("Unsupported parameterset: " + set.getClass().getName());
        }
    }


    public void addZonePriceSettings(ZonePricesParameterSet settings) {

        // check if every tarif module is added to the settings...
        this.zonePriceSettings.add(settings);
        super.addParameterSet(settings);
    }

    public List<ZonePricesParameterSet> getZonePricesParameterSets() {
        return this.zonePriceSettings;
    }

    public Map<Integer, Double> getAllFares(){

        Map <Integer, Double> priceZoneAssignment = new HashMap<>();
        zonePriceSettings.stream().forEach(zonePricesParameterSet -> {
            priceZoneAssignment.put(zonePricesParameterSet.getNumberZones(), zonePricesParameterSet.getTicketPrice());
        });
        return priceZoneAssignment;
    }

    public static class ZonePricesParameterSet extends ReflectiveConfigGroup {

        private static final String TYPE = "zonePrices";

        private static final String NUMBER_ZONES = "numberZones";
        private static final String TICKET_PRICE = "ticketPrice";

        private Integer numberZones;
        private Double ticketPrice;

        public ZonePricesParameterSet() {
            super(TYPE);
        }

        @StringGetter(NUMBER_ZONES)
        public Integer getNumberZones(){
            return numberZones;
        }

        @StringSetter(NUMBER_ZONES)
        public void setNumberZones(Integer numberZones){
            this.numberZones = numberZones;
        }

        @StringGetter(TICKET_PRICE)
        public Double getTicketPrice(){
            return ticketPrice;
        }

        @StringSetter(TICKET_PRICE)
        public void setTicketPrice(Double ticketPrice){
            this.ticketPrice = ticketPrice;
        }

    }

}
