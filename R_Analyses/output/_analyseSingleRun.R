library(tidyverse)
library(sf)
library(tmap)
library(ggplot2)
library(ggmap)
library(lubridate)
options(scipen=999)

RUNNAME <- "stuttgart-OldScoring-bikeFriendly-cont-10pct"
SAMPLESIZE <- 0.1


## create list to collect results ##
resultCollection <- list()





################################################################################
##################### PART 1: Analyse link counts #############################
################################################################################

##### 0 - read and merge data #####

### read car, bike and bike counting station counts ###
carCounts <- read.csv2(paste0(RUNNAME, "/", RUNNAME, ".carLinkCounts.csv"))
bikeCounts <- read.csv2(paste0(RUNNAME, "/", RUNNAME, ".bikeLinkCounts.csv"))
bikeCountingStations <- read.csv2(paste0(RUNNAME, "/", RUNNAME, ".bikeCountingStationCounts.csv"))



### read network ###
t.net <- read.csv("../input/stuttgart-v3.0/networkAttributesStuttgartBikeFriendly.csv") #adjust as needed



### read stuttgart shapefile ###
stuttgart <- st_read("../input/stuttgart-v2.0/lh-stuttgart.shp")



### process network data into vector-based modelist and geodata into sf, tag bike infrastructure, tag links that lie in stuttgart ###

#create function to process the network
processNetwork <- function(t.net){
  
  #create mode vector, create from- and to-points
  net <- t.net %>% 
    mutate(allowedModesVector = str_sub(allowedModes, start = 2, end = -2)) %>% 
    mutate(allowedModesVector = str_split(allowedModesVector, ',')) %>% 
    mutate(fromPoint = paste(fromX, fromY, fromZ, sep = ","),
           fromPoint = str_split(fromPoint, ','),
           toPoint = paste(toX, toY, toZ, sep = ","),
           toPoint = str_split(toPoint, ',')) #get all from/to coords into one vector to make the following loop easier
  
  #Loop to create linestrings from to- and from-points (not preferred solution, but surprisingly fast)
  res <- list()
  temp <- NULL
  for (i in c(1:length(net$linkId))){
    
    res[[i]] <- st_linestring(
      x = rbind(as.numeric(net$fromPoint[[i]]), 
                as.numeric(net$toPoint[[i]])),
      dim = "XYZ"
    )
    
  }
  
  #put linestrings into data frame and add crs 
  net$geometry <- st_sfc(res, crs = 25832)
  net <- st_sf(net)
  
  #calculate link lengths
  net$length <- st_length(net)
  
  #tag street type
  net <- net %>% 
    mutate(
      typeIsSideroad = ifelse(
        type %in% c("minor", "unclassified", "residential", "living_street", 
                    "service", "pedestrian", "track", "footway", "path", "cycleway"),
        T, F
      ),
      typeIsMainroad = typeIsSideroad == F
    ) %>% 
    mutate(
      streettypeString = ifelse(typeIsSideroad, "sideroad", "mainroad")
    )
  
  #tag bike infrastructure
  net <- net %>% 
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
    ) %>% 
    #determine highest infrastructure category on link
    mutate(
      infraString = "None",
      infraString = ifelse(infraIsLane, "Lane", infraString),
      infraString = ifelse(infraIsPath, "Path", infraString),
      infraString = ifelse(infraIsPBL, "PBL", infraString)
    ) %>% 
    #determine if bikes are allowed on the link
    mutate(
      bikeAllowed = grepl('bike', allowedModes)
    )
  
  #tag road surface
  net <- net %>% 
    mutate(isAsphalt = surface %in% c("excellent", "paved", "asphalt;paved", "asphalt", "concrete", "concrete:lanes", "concrete_plates",
                                      "concrete:plates", "paving_stones", "paving_stones:3", "paving_stones:35", "paving_stones:30",
                                      "asphalt;paving_stones:35") |
             (surface == "" & type %in% c("primary", "primary_link", "secondary", "secondary_link", 
                                          "tertiary", "tertiary_link", "residential", "cycleway")),
           isCobbled = surface %in% c("cobblestone", "cobblestone (bad)", "sett", "grass_paver", 
                                      "cobblestone;flattened", "cobblestone:flattened",
                                      "bricks"),
           isGravel = isAsphalt==F & isCobbled==F) %>% 
    #determine surface as single string
    mutate(
      surfaceString = "macadam",
      surfaceString = ifelse(isCobbled, "cobble", surfaceString),
      surfaceString = ifelse(isAsphalt, "asphalt", surfaceString)
    )
  
    
  
  #return processed network
  return(net)
  
}

#process the network
net <- processNetwork(t.net)

#tag links that lie in Stuttgart
net$isInStutt <- as.vector(st_within(net, stuttgart, sparse = F))

#remove pt links
net <- net %>% 
  filter(!grepl('pt', allowedModes))



### merge data from link counts into network ###
net$bikeCount <- bikeCounts$bikeCount[match(net$linkId, bikeCounts$Link)]
net$carCount <- carCounts$carCount[match(net$linkId, carCounts$Link)]





##### 0.5 - call subscripts #####

## call subscripts and paste results to result list ##

#bike counter comparison
source("subscript_compareCounts.R")
resultCollection$bccDataFrame <- bikeCounterComparison
resultCollection$bccOtherResults <- resBikeCounterComparison
resultCollection$bccGEH2017 <- GEH2017
resultCollection$bccGEH2022 <- GEH2022
rm(bikeCounterComparison, resBikeCounterComparison, GEH2017, GEH2022)





##### 1 - analyse distributions of counts over link attributes (type, bike infra, ...) #####

### 1.1 analyse distribution of bike and car counts over street type ###
analysis <- st_drop_geometry(net) %>% 
  filter(isInStutt) %>% 
  group_by(streettypeString) %>% 
  summarise(bikeCount = sum(bikeCount, na.rm=T),
            carCount = sum(carCount, na.rm=T),
            bikeDist = sum(bikeCount * length, na.rm = T),
            carDist = sum(carCount * length, na.rm = T),
            length = sum(length)) %>% 
  mutate(bikeCountPct = (bikeCount / sum(bikeCount, na.rm=T)) * 100,
         carCountPct = (carCount / sum(carCount, na.rm=T)) * 100,
         lengthPct = (length / sum(length)) * 100)
resultCollection$cntDistrByStrtype <- analysis



### 1.2 analyse distribution of bike and car counts over infrastructure type ###
analysis <- st_drop_geometry(net) %>% 
  filter(isInStutt) %>% 
  group_by(infraString) %>% 
  summarise(bikeCount = sum(bikeCount, na.rm=T),
            carCount = sum(carCount, na.rm=T),
            bikeDist = sum(bikeCount * length, na.rm = T),
            carDist = sum(carCount * length, na.rm = T),
            length = sum(length)) %>% 
  mutate(bikeCountPct = (bikeCount / sum(bikeCount, na.rm=T)) * 100,
         carCountPct = (carCount / sum(carCount, na.rm=T)) * 100,
         lengthPct = (length / sum(length)) * 100)
resultCollection$cntDistrByInfra <- analysis



### 1.3 analyse distribution of bike and car counts over surface type ###
analysis <- st_drop_geometry(net) %>% 
  filter(isInStutt) %>% 
  group_by(surfaceString) %>% 
  summarise(bikeCount = sum(bikeCount, na.rm=T),
            carCount = sum(carCount, na.rm=T),
            bikeDist = sum(bikeCount * length, na.rm = T),
            carDist = sum(carCount * length, na.rm = T),
            length = sum(length)) %>% 
  mutate(bikeCountPct = (bikeCount / sum(bikeCount, na.rm=T)) * 100,
         carCountPct = (carCount / sum(carCount, na.rm=T)) * 100,
         lengthPct = (length / sum(length)) * 100)
resultCollection$cntDistrBySurface <- analysis



### 1.4 analyse distribution of bike and car counts over street, infrastructure and surface type ###
analysis <- st_drop_geometry(net) %>% 
  filter(isInStutt) %>% 
  group_by(streettypeString, infraString, surfaceString) %>% 
  summarise(bikeCount = sum(bikeCount, na.rm=T),
            carCount = sum(carCount, na.rm=T),
            bikeDist = sum(bikeCount * length, na.rm = T),
            carDist = sum(carCount * length, na.rm = T),
            length = sum(length)) %>% 
  ungroup() %>% 
  mutate(bikeCountPct = (bikeCount / sum(bikeCount, na.rm=T)) * 100,
         carCountPct = (carCount / sum(carCount, na.rm=T)) * 100,
         lengthPct = (length / sum(length)) * 100)
resultCollection$cntDistrByAllDCAttr <- analysis






##### plot tests #####

#tmap seems like the better option, because the tiles obtainable with ggmap right now just kind of suck
# tmap_mode("view")
# tm_basemap("OpenStreetMap.Mapnik") +
#   tm_shape(net[which(net$isInStutt),]) +
#   tm_lines("infraString") +
#   tm_tiles("OpenStreetMap.Mapnik")
# 
# #download & plot static tiles
# test <- tmaptools::read_osm(stuttgart)
# tmap_mode("plot")
# tm_shape(test) +
#   tm_rgb() 
# 
# tm_shape(test) +
#   tm_rgb() +
#   tm_shape(net %>% filter(isInStutt) %>% mutate(infraString = factor(infraString, levels = c("None", "Lane", "Path", "PBL")))) +
#   tm_lines("infraString", palette = viridisLite::viridis(4)) 
# 
# 
# map <- get_stamenmap(unname(st_bbox(st_transform(stuttgart, crs = 4326))), maptype = c("toner-labels"), zoom = 14)
# ggmap(map)





################################################################################
##################### PART 2: Analyse output trips #############################
################################################################################
#(can be executed independently from PART 1, only needs stuff before PART 1)
rm(list = ls()[which(ls()!="RUNNAME" & ls()!="resultCollection")])



##### 0 - read and filter data #####

## read stuttgart shapefile ##
stuttgart <- st_read("../input/stuttgart-v2.0/lh-stuttgart.shp")

## read output trips ##
trips <- read.csv2(paste0(RUNNAME, "/", RUNNAME, ".output_trips.csv"))


## Bring data into sf form and filter trips by inhabitants after the Wohnortprinzip ## 
orig <- st_as_sf(trips, coords = c("start_x", "start_y"), crs = 25832, remove = F)

filterAgentsByWohnortprinzip <- function(data){
  
  data$isInStutt <- as.vector(st_within(data, stuttgart, sparse = F))
  
  filtered <- data %>% 
    filter(trip_number==1) %>% 
    filter(isInStutt)
  
  return(filtered)
  
}

inhabitants <- filterAgentsByWohnortprinzip(orig)

tripsStutt <- trips[which(trips$person %in% inhabitants$person),]

#ggplot() + geom_sf(data = stuttgart) + geom_sf(data = orig[which(orig$person %in% inhabitants$person & orig$trip_number==1),])





##### 1 - Analyse trip distance class distribution (based on table A W12) #####

## partition trips into distance classes based on euclidean distance ##
breaks <- c(-Inf, 500, 1000, 2000, 5000, 10000, 20000, 50000, 100000, Inf)
labs <- c("<0,5km", "0,5-<1km", "1km-<2km", "2km-<5km", "5km-<10km", "10km-<20km", "20km-<50km", "50km-<100km", ">100km")
tripsStutt$distClass <- labs[findInterval(tripsStutt$euclidean_distance, breaks)]
tripsStutt$distClass <- factor(tripsStutt$distClass, levels = labs)


## analyse distribution over distance classes
distanceClassAnalysis <- data.frame(
  cat = names(table(tripsStutt$distClass)),
  absSNZ = as.vector(table(tripsStutt$distClass)),
  relSNZ = as.vector(prop.table(table(tripsStutt$distClass))),
  absMID = c(864, 935, 1103, 1751, 1170, 850, 495, 177, 139)
) %>% 
  mutate(relMID = absMID / sum(absMID),
         diffRel = relSNZ - relMID)
resultCollection$trpDistrByDistClass <- distanceClassAnalysis





##### 2 - Analyse trip duration class distribution (based on table A W9) #####

## reformat trip durations ##
tripsStutt <- tripsStutt %>% 
  mutate(
    trav_time_asDate = hms(trav_time),
    trav_time_asSec = as.numeric(seconds(trav_time_asDate))
  )


## partition trips into duration classes based on travel time ##
breaks <- c(-Inf, 5*60, 10*60, 15*60, 20*60, 30*60, 45*60, 60*60, Inf)
labs <- c("<5min.", "5min.-<10 min.", "10min.-<15min.", "15min.-<20min.", "20min.-<30min.", "30min.-<45min.", "45min.-<60min.", ">60min.")
tripsStutt$durClass <- labs[findInterval(tripsStutt$trav_time_asSec, breaks)]
tripsStutt$durClass <- factor(tripsStutt$durClass, levels = labs)


## analyse distribution over duration classes ##
durationClassAnalysis <- data.frame(
  cat = names(table(tripsStutt$durClass)),
  absMATSIM = as.vector(table(tripsStutt$durClass)),
  relMATSIM = as.vector(prop.table(table(tripsStutt$durClass)))*100,
  relMID = c(1, 13, 15, 18, 15, 19, 7, 11)
) %>% 
  mutate(diffRel = relMATSIM-relMID)
resultCollection$trpDistrByDurClass <- durationClassAnalysis


## analyse distribution over duration classes and modes ##
durationClassByModeAnalysis <- tripsStutt %>% 
  group_by(longest_distance_mode, durClass) %>% 
  summarise(absMATSIM = length(traveled_distance)) %>% 
  group_by(longest_distance_mode) %>% 
  mutate(relPerMode = absMATSIM/sum(absMATSIM))
resultCollection$trpDistrByDurClassAndMode <- durationClassByModeAnalysis





##### 3 - analyse kilometers driven/made by agents #####
distByModeAnalysis <- tripsStutt %>% 
  group_by(longest_distance_mode) %>% 
  summarise(net = sum(traveled_distance, na.rm=T),
            euclidean = sum(euclidean_distance, na.rm=T))
resultCollection$distByModeAnalysis <- distByModeAnalysis





##### Final: Write out results #####
writexl::write_xlsx(resultCollection, path = paste0(RUNNAME, "/", RUNNAME, "_resultCollection.xlsx"))


