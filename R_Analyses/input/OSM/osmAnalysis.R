##### Init - load packages #####

library(tidyverse)
library(sf)
library(osmdata)


##### Init - read data #####
stuttgart <- st_read("../stuttgart-v2.0/lh-stuttgart.shp")

ggplot(stuttgart) + 
  geom_sf()

##### 1 - get data #####

## Download OSM data to hard drive for reusability
q <- opq(bbox = getbb('Stuttgart')) %>%  #Funktioniert irgendwie leider nicht mit den Werten aus dem Shape-File; ggf. falsches BB-Format?
  add_osm_feature(key="highway") #%>% 
  #osmdata_xml(file = "highway_stuttgart.osm")

## read data from hard drive
t.data <- osmdata_sf(q, doc = "highway_stuttgart.osm")

## extract highway lines (i will ignore polygons, multilines and multipolygons)
lines <- t.data$osm_lines
str(lines)
names(lines)

## check data with plot
ggplot() +
  geom_sf(data = stuttgart, fill = "black") +
  geom_sf(data = lines, colour = "blue") +
  theme_bw()


## get overview of data
table(lines$highway)
table(lines$lcn)
table(lines$bicycle)
names(lines)[grepl('bicycle',names(lines))]
names(lines)[grepl('lcn',names(lines))]
table(lines$bicycle[which(lines$highway=="path")]) #insg. 11.225 path-Links -> Rest ist NA
table(lines$bicycle[which(lines$highway=="track")]) #insg. 8220 track-Links -> Rest ist NA




##### 2 - get map of local cycle network #####
ggplot() +
  geom_sf(data = stuttgart) +
  geom_sf(data = lines[which(lines$lcn=="yes"),], colour = "black") +
  theme_bw()
ggsave(filename = "2_lcn_stuttgart.png")




##### 3 - select new cycleways #####

## According to OneNote (230419), ignoring cycleway-key-taggings
ggplot() +
  geom_sf(data = stuttgart) +
  geom_sf(data = lines[
    which(lines$highway %in% c("footway", "path", "track") & lines$bicycle %in% c("yes", "designated")),
  ], colour = "blue") +
  geom_sf(data = lines[
    which(lines$lcn=="yes"),
  ], colour = "black") +
  geom_sf(data = lines[
    which(lines$highway=="cycleway"),
  ], colour = "green") +
  theme_bw() #-> adds some parts, but all in all still often incoherent bike network (although main roads might be sufficiently given)
ggsave(filename = "3_newCyclewaySelection_stuttgart.png")


## According to OneNote (230503), including cycleway-key-taggings, IGNORING LCN FOR ADDITIONAL TRACKS
ggplot() +
  geom_sf(data = stuttgart) +
  geom_sf(data = lines[
    which(lines$highway %in% c("footway", "path", "track", "pedestrian") & lines$bicycle %in% c("yes", "designated")),
  ], colour = "blue") +
  geom_sf(data = lines[
    which(lines$lcn=="yes"),
  ], colour = "black") +
  geom_sf(data = lines[
    which(lines$highway=="cycleway" | lines$cycleway %in% c("both", "lane", "opposite", "opposite_lane", "opposite_track", 
                                                            "track")),
  ], colour = "green") +
  theme_bw() #-> adds some parts, but all in all still often incoherent bike network (although main roads might be sufficiently given)
ggsave(filename = "4_newCyclewaySelection_stuttgart_230503.png")


## According to OneNote (230419) but without filtering footpaths, paths and tracks for explicit bike allowance
ggplot() +
  geom_sf(data = stuttgart) +
  geom_sf(data = lines[
    which(lines$highway %in% c("footway", "path", "track") & !(lines$bicycle %in% c("no", "dismount", "use_sidepath"))),
  ], colour = "blue") +
  geom_sf(data = lines[
    which(lines$lcn=="yes"),
  ], colour = "black") +
  geom_sf(data = lines[
    which(lines$highway=="cycleway"),
  ], colour = "green") +
  theme_bw() #-> adds by far too much
