#This script serves to compare the versions 2 and 3 of the stuttgart network
library(tidyverse)
library(sf)
options(scipen=999)

##### Read data #####
t.v2 <- read.csv("../stuttgart-v2.0/networkAttributes.csv")
t.v3 <- read.csv("networkAttributesStuttgartV3.csv")


## Look at data ##
head(t.v2)
head(t.v3)
#There is some loss regarding the coord. accuracy, but as MATSim usually uses cartesian coords,
#those should be in a range <0.1m
table(t.v2$allowedModes)
table(t.v3$allowedModes)





##### Data preparation #####

## get vector of allowed modes, prepare creation of link geometries ##
v2 <- t.v2 %>% 
  mutate(allowedModesVector = str_sub(allowedModes, start = 2, end = -2)) %>% 
  mutate(allowedModesVector = str_split(allowedModesVector, ',')) %>% 
  mutate(fromPoint = paste(fromX, fromY, fromZ, sep = ","),
         fromPoint = str_split(fromPoint, ','),
         toPoint = paste(toX, toY, toZ, sep = ","),
         toPoint = str_split(toPoint, ',')) #get all from/to coords into one vector to make the following loop easier
v3 <- t.v3 %>% 
  mutate(allowedModesVector = str_sub(allowedModes, start = 2, end = -2)) %>% 
  mutate(allowedModesVector = str_split(allowedModesVector, ',')) %>% 
  mutate(fromPoint = paste(fromX, fromY, fromZ, sep = ","),
         fromPoint = str_split(fromPoint, ','),
         toPoint = paste(toX, toY, toZ, sep = ","),
         toPoint = str_split(toPoint, ',')) #get all from/to coords into one vector to make the following loop easier



## create geometries of links ##

###
#for v2: Loop to create linestrings (not preferred solution, but surprisingly fast)
res <- list()
temp <- NULL
for (i in c(1:length(v2$linkId))){
  
  res[[i]] <- st_linestring(
    x = rbind(as.numeric(v2$fromPoint[[i]]), 
              as.numeric(v2$toPoint[[i]])),
    dim = "XYZ"
  )
  
}

#put linestrings into data frame and add crs 
v2$geometry <- st_sfc(res, crs = 25832)
v2 <- st_sf(v2)
###

###
#repeat for v3
res <- list()
temp <- NULL
for (i in c(1:length(v3$linkId))){
  
  res[[i]] <- st_linestring(
    x = rbind(as.numeric(v3$fromPoint[[i]]), 
              as.numeric(v3$toPoint[[i]])),
    dim = "XYZ"
  )
  
}
#put linestrings into data frame and add crs 
v3$geometry <- st_sfc(res, crs = 25832)
v3 <- st_sf(v3)
###

#Tests for loop
#temp <- rbind(as.numeric(dat$fromPoint[[1]]),
#              as.numeric(dat$toPoint[[1]]))
#temp
#temp <- st_linestring(x = temp, dim = "XYZ")
#ggplot() + geom_sf(data = temp)

rm(res, i, temp)



### Filter for links that lie in Stuttgart ###

##read in shapefile for stuttgart
stuttgart <- st_read("../stuttgart-v2.0/lh-stuttgart.shp")


#v2
v2Stutt <- v2 
v2Stutt$within <- as.vector(st_within(v2Stutt, stuttgart, sparse = F))
v2Stutt <- v2Stutt %>% 
  filter(within) %>% 
  mutate(bikeAllowed = grepl('bike', allowedModes)) %>% #Add indicator if bike is allowed 
  filter(!grepl('pt', allowedModes)) #Filter out pt links
#v3
v3Stutt <- v3 
v3Stutt$within <- as.vector(st_within(v3Stutt, stuttgart, sparse = F))
v3Stutt <- v3Stutt %>% 
  filter(within) %>% 
  mutate(bikeAllowed = grepl('bike', allowedModes)) %>% #Add indicator if bike is allowed 
  filter(!grepl('pt', allowedModes)) #Filter out pt links





##### First data analyses (of all Links) #####


## compare distributions of OSM-key highway for entire network ##
table(v2$type)
table(v3$type)
length(v3$type[which(! v3$type %in% c("cycleway", "footway", "path", "pedestrian", "track"))])



## compare entire network lengths ##
sum(st_length(v2))/1000
sum(st_length(v3))/1000
sum(st_length(v3[which(! v3$type %in% c("cycleway", "footway", "path", "pedestrian", "track")),]))/1000



### create plots of network ###

#v2 %>% 
 # filter(!grepl('pt', allowedModes)) %>% 
#  ggplot() +
#  geom_sf(colour = "black") +
#  theme_bw()
#ggsave(filename = "plot_network_v2.png", height = 8, width = 10)

#v3 %>% 
#  filter(!grepl('pt', allowedModes)) %>% 
#  ggplot() +
#  geom_sf(colour = "black") +
#  theme_bw()
#ggsave(filename = "plot_network_v3.png", height = 8, width = 10)








##### Data Analysis (of links in Stuttgart) #####

### Tag WTP categories ###

## Tag bike infrastructure ##
v2Stutt <- v2Stutt %>% 
  mutate(
    infraIsLane = ifelse(
      cyclewaytype %in% c("lane", "yes", "right", "opposite", "left", 
                          "lane;opposite_lane", "both", "cyclestreet", "opposite_lane")
      # | lcn=="yes"
      , T, F
      ),
    infraIsPath = ifelse(
      type %in% c("pedestrian", "footway"), T, F
      ),
    infraIsPBL = ifelse(
      cyclewaytype %in% c("track", "track;opposite_track", "track;opposite_lane", "opposite_track") |
        type %in% c("path", "cycleway", "track"), T, F
    )
  )

v3Stutt <- v3Stutt %>% 
  mutate(
    infraIsLane = ifelse(
      cyclewaytype %in% c("lane", "yes", "right", "opposite", "left", 
                          "lane;opposite_lane", "both", "cyclestreet", "opposite_lane") | 
        lcn=="yes", T, F
    ),
    infraIsPath = ifelse(
      type %in% c("pedestrian", "footway"), T, F
    ),
    infraIsPBL = ifelse(
      cyclewaytype %in% c("track", "track;opposite_track", "track;opposite_lane", "opposite_track") |
        type %in% c("path", "cycleway", "track"), T, F
    )
  )



### Plot WTP categories ###

## plot bike infrastructure ##
#plot v2
ggplot() +
  geom_sf(data = stuttgart, aes(fill = "Stuttgart"), show.legend = FALSE) +
  geom_sf(data = v2Stutt, aes(colour = "Straße ohne Radinfrastruktur")) +
  geom_sf(data = v2Stutt[which(v2Stutt$bikeAllowed & v2Stutt$infraIsLane),], aes(colour = "Radfahrstreifen")) +
  #geom_sf(data = v2Stutt[which(v2Stutt$bikeAllowed & v2Stutt$infraIsPath),], aes(colour = "Radweg")) +
  geom_sf(data = v2Stutt[which(v2Stutt$bikeAllowed & v2Stutt$infraIsPBL),], aes(colour = "geschützter Radfahrstreifen")) +
  theme_bw() +
  theme(axis.ticks = element_blank(), axis.text.x = element_blank(), axis.text.y = element_blank(),
        panel.grid = element_blank(), panel.border = element_blank(), legend.position = "bottom") +
  scale_color_manual(name = "Legende",
                     breaks = c("Straße ohne Radinfrastruktur", "Radfahrstreifen", "Radweg", "geschützter Radfahrstreifen"),
                     values = c("Straße ohne Radinfrastruktur" = "black",
                                "Radfahrstreifen" = "blue", "Radweg" = "green",
                                "geschützter Radfahrstreifen" = "red")) +
  scale_fill_manual(name = "Stuttgart",
                      breaks = c("Stuttgart"), 
                      values = c("Stuttgart" = "white"))
ggsave(filename = "plot_bikeInfra_v2.png", height = 8, width = 8)

#plot v3
ggplot() +
  geom_sf(data = stuttgart, aes(fill = "Stuttgart"), show.legend = FALSE) +
  geom_sf(data = v3Stutt, aes(colour = "Straße ohne Radinfrastruktur")) +
  geom_sf(data = v3Stutt[which(v3Stutt$bikeAllowed & v3Stutt$infraIsLane),], aes(colour = "Radfahrstreifen")) +
  geom_sf(data = v3Stutt[which(v3Stutt$bikeAllowed & v3Stutt$infraIsPath),], aes(colour = "Radweg")) +
  geom_sf(data = v3Stutt[which(v3Stutt$bikeAllowed & v3Stutt$infraIsPBL),], aes(colour = "geschützter Radfahrstreifen")) +
  theme_bw() +
  theme(axis.ticks = element_blank(), axis.text.x = element_blank(), axis.text.y = element_blank(),
        panel.grid = element_blank(), panel.border = element_blank(), legend.position = "bottom") +
  scale_color_manual(name = "Legende",
                     breaks = c("Straße ohne Radinfrastruktur", "Radfahrstreifen", "Radweg", "geschützter Radfahrstreifen"),
                     values = c("Straße ohne Radinfrastruktur" = "black",
                                "Radfahrstreifen" = "blue", "Radweg" = "green",
                                "geschützter Radfahrstreifen" = "red")) +
  scale_fill_manual(name = "Stuttgart",
                    breaks = c("Stuttgart"),
                    values = c("Stuttgart" = "white"))
ggsave(filename = "plot_bikeInfra_v3.png", height = 8, width = 8)


##compare network lengths (für v2 und v3 bei Radinfra-Tagging mit und ohne bikeAllowed gleich)
sum(st_length(v2Stutt))/1000
sum(st_length(v2Stutt[which(v2Stutt$bikeAllowed & v2Stutt$infraIsLane & v2Stutt$infraIsPath==F & v2Stutt$infraIsPBL==F),]))/1000
sum(st_length(v2Stutt[which(v2Stutt$bikeAllowed & v2Stutt$infraIsPath & v2Stutt$infraIsPBL==F),]))/1000
sum(st_length(v2Stutt[which(v2Stutt$bikeAllowed & v2Stutt$infraIsPBL),]))/1000
sum(st_length(v2Stutt[which(v2Stutt$infraIsLane | v2Stutt$infraIsPath | v2Stutt$infraIsPBL),]))


sum(st_length(v3Stutt))/1000
sum(st_length(v3Stutt[which(v3Stutt$bikeAllowed & v3Stutt$infraIsLane & v3Stutt$infraIsPath==F & v3Stutt$infraIsPBL==F),]))/1000
sum(st_length(v3Stutt[which(v3Stutt$bikeAllowed & v3Stutt$infraIsPath & v3Stutt$infraIsPBL==F),]))/1000
sum(st_length(v3Stutt[which(v3Stutt$bikeAllowed & v3Stutt$infraIsPBL),]))/1000
sum(st_length(v3Stutt[which(v3Stutt$infraIsLane | v3Stutt$infraIsPath | v3Stutt$infraIsPBL),]))
sum(st_length(v3Stutt[which((v3Stutt$infraIsLane | v3Stutt$infraIsPath | v3Stutt$infraIsPBL) &
                              v3Stutt$allowedModes!="[bike]"),]))
sum(st_length(v3Stutt[which(v3Stutt$allowedModes=="[bike]"),]))
head(v3Stutt)
v3Stutt$length <- st_length(v3Stutt)
aggregate(length~allowedModes, data = v3Stutt, FUN = sum)
aggregate(length~type, data = v3Stutt, FUN = sum)
