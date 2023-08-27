library(tidyverse)
library(ggplot2)

#RUNNAME <- "stuttgart-DCScoring-baseCase-10pct"
#SAMPLESIZE <- 0.1


## read data ##

extData2017 <- read.csv2("externalCountingData/dtvw5-bikeCounters-stuttgart-2017.csv")
extData2022 <- read.csv2("externalCountingData/dtvw5-bikeCounters-stuttgart-2022.csv")
#bikeCountingStations <- read.csv2(paste0(RUNNAME, "/", RUNNAME, ".bikeCountingStationCounts.csv"))


## distinguish bike counting stations between links actually counted by counter and links just included to check for cars ##
bikeCountingStations$hasActualCounter <- recode(bikeCountingStations$Link,
                                                "2993895710004f" = TRUE, 
                                                "2993895710004r" = TRUE,
                                                "2993895700003f" = FALSE,
                                                "2615633160000f" = FALSE,
                                                "5957276710002f" = FALSE,
                                                "5957276710002r" = FALSE,
                                                "401040430002f" = TRUE,
                                                "401040430002r" = TRUE,
                                                "3585536500004f" = TRUE,
                                                "3585536500004r" = TRUE,
                                                "3585536510004f" = TRUE,
                                                "10968008150004f" = TRUE,
                                                "10272926360000f" = TRUE,
                                                "10272926360000r" = TRUE,
                                                "3536362270003f" = TRUE,
                                                "3467623440004f" = TRUE,
                                                "3467623440004r" = TRUE,
                                                "1750316500005f" = TRUE,
                                                "1750316500005f_bike-reverse" = TRUE,
                                                "3777509370000f" = TRUE,
                                                "3777509370000r" = TRUE,
                                                "248127500000f" = FALSE,
                                                "227009470000f" = FALSE,
                                                "237199820003f" = TRUE,
                                                "237199820003r" = TRUE,
                                                "7629370820007f" = TRUE,
                                                "7629370820007r" = TRUE,
                                                "262444090001f" = FALSE,
                                                "262444090001r" = FALSE,
                                                "2993863910015f" = TRUE,
                                                "2993863910015r" = TRUE,
                                                "2977618270000f" = FALSE,
                                                "1966731520017f" = FALSE,
                                                "3684442310011f" = TRUE,
                                                "3684442310011r" = TRUE,
                                                "4369205380002f" = FALSE,
                                                "4369205380002r" = FALSE,
                                                "3832380570013f" = TRUE,
                                                "3832380570013r" = TRUE,
                                                "290371860010f" = TRUE,
                                                "290371860010r" = TRUE,
                                                "3023316010000f" = FALSE,
                                                "3023316010000r" = FALSE,
                                                "3570056810004f" = TRUE,
                                                "3570056810004r" = TRUE,
                                                "3570049870031f" = TRUE,
                                                "3570049870031r" = TRUE,
                                                "2555251750007f" = TRUE,
                                                "2555251750007r" = TRUE,
                                                "393429710001f" = FALSE,
                                                "393429710001r" = FALSE
                                                )


## join external counts ##
bikeCounterComparison <- extData2022
bikeCounterComparison$X <- NULL
names(bikeCounterComparison)[which(names(bikeCounterComparison)=="meanCountsPerDay")] <- "meanCountsPerDay_2022"
bikeCounterComparison$meanCountsPerDay_2017 <- extData2017$meanCountsPerDay[match(
  bikeCounterComparison$counter_site, extData2017$counter_site
)]
rm(extData2017, extData2022)


## add info necessary to match names gotten from actual station data and names gotten from MATSim ##
bikeCounterComparison$MATSimName <- recode(bikeCounterComparison$counter_site,
                                           "Am Kräherwald" = "Am Kraeherwald",
                                           "Böblinger Straße" = "Boeblinger Strasse",
                                           "Inselstraße" = "Inselstrasse",
                                           "Kirchheimer Straße" = "Kirchheimer Strasse",
                                           "König-Karls-Brücke Barometer" = "Koenig-Karls-Bruecke Barometer",
                                           "Kremmlerstraße" = "Kremmlerstrasse",
                                           "Lautenschlager Straße" = "Lautenschlager Strasse",
                                           "Neckartalstraße" = "Neckartalstrasse",
                                           "Samaraweg" = "Samaraweg",
                                           "Solitudestraße" = "Solitudestrasse",
                                           "Stuttgarter Straße" = "Stuttgarter Strasse",
                                           "Taubenheimstraße" = "Taubenheimstrasse",
                                           "Tübinger Straße" = "Tuebinger Strasse",
                                           "Waiblinger Straße" = "Waiblinger Strasse",
                                           "Waldburgstraße" = "Waldburgstrasse")


## aggregate MATSim data and join it to external counts ##

# only bicycle links
tmp <- bikeCountingStations %>% 
  filter(hasActualCounter) %>% 
  group_by(Station) %>% 
  summarise(count = sum(bikeCount))
bikeCounterComparison$MATSimCycleway <- tmp$count[match(bikeCounterComparison$MATSimName, tmp$Station)]
bikeCounterComparison$MATSimCycleway <- bikeCounterComparison$MATSimCycleway * (1/SAMPLESIZE)

# only car links
tmp <- bikeCountingStations %>% 
  filter(hasActualCounter==F) %>% 
  group_by(Station) %>% 
  summarise(count = sum(bikeCount))
bikeCounterComparison$MATSimCarLink <- tmp$count[match(bikeCounterComparison$MATSimName, tmp$Station)]
bikeCounterComparison$MATSimCarLink[which(is.na(bikeCounterComparison$MATSimCarLink))] <- 0
bikeCounterComparison$MATSimCarLink <- bikeCounterComparison$MATSimCarLink * (1/SAMPLESIZE)

bikeCounterComparison$MATSimAll <- bikeCounterComparison$MATSimCycleway + bikeCounterComparison$MATSimCarLink

rm(tmp, bikeCountingStations)


## calculate sum of squared errors (for cycleways only) ##

res <- data.frame(
  counters = c("only bike links", "bike and car links"),
  SSE_2017 = c(sum((bikeCounterComparison$MATSimCycleway[which(is.na(bikeCounterComparison$meanCountsPerDay_2017)==F)] - 
                     bikeCounterComparison$meanCountsPerDay_2017[which(is.na(bikeCounterComparison$meanCountsPerDay_2017)==F)])^2),
               sum((bikeCounterComparison$MATSimAll[which(is.na(bikeCounterComparison$meanCountsPerDay_2017)==F)] - 
                      bikeCounterComparison$meanCountsPerDay_2017[which(is.na(bikeCounterComparison$meanCountsPerDay_2017)==F)])^2)
               ),
  SSE_2022 = c(sum((bikeCounterComparison$MATSimCycleway - bikeCounterComparison$meanCountsPerDay_2022)^2),
               sum((bikeCounterComparison$MATSimAll - bikeCounterComparison$meanCountsPerDay_2022)^2)
               )
)


## calculate relative and mean relative deviations (for cycleways only) ##
bikeCounterComparison <- bikeCounterComparison %>% 
  mutate(relDev2017CW = (MATSimCycleway-meanCountsPerDay_2017)/meanCountsPerDay_2017,
         relDev2022CW = (MATSimCycleway-meanCountsPerDay_2022)/meanCountsPerDay_2022,
         relDev2017All = (MATSimAll-meanCountsPerDay_2017)/meanCountsPerDay_2017,
         relDev2022All = (MATSimAll-meanCountsPerDay_2022)/meanCountsPerDay_2022)

res$meanRelDev2017 <- c(mean(bikeCounterComparison$relDev2017CW, na.rm=T),
                        mean(bikeCounterComparison$relDev2017All, na.rm=T))
res$meanRelDev2022CW <- c(mean(bikeCounterComparison$relDev2022CW), 
                          mean(bikeCounterComparison$relDev2022All))


## calculate RMSE and %RMSE ##

#2017
temp <- bikeCounterComparison %>% 
  filter(is.na(meanCountsPerDay_2017)==F) %>% 
  mutate(sqDiffCW = (MATSimCycleway - meanCountsPerDay_2017)^2,
         sqDiffAll = (MATSimAll - meanCountsPerDay_2017)^2) %>% 
  summarise(rmseCW = sqrt(mean(sqDiffCW)),
            rmseAll = sqrt(mean(sqDiffAll)))
res$RMSE2017 <- c(temp$rmseCW, temp$rmseAll)

#2022
temp <- bikeCounterComparison %>% 
  mutate(sqDiffCW = (MATSimCycleway - meanCountsPerDay_2022)^2,
         sqDiffAll = (MATSimAll - meanCountsPerDay_2022)^2) %>% 
  summarise(rmseCW = sqrt(mean(sqDiffCW)),
            rmseAll = sqrt(mean(sqDiffAll)))
res$RMSE2022 <- c(temp$rmseCW, temp$rmseAll)


## calculate pearson corr-coeff ##

#2017
temp <- bikeCounterComparison %>% 
  filter(is.na(meanCountsPerDay_2017)==F) %>% 
  summarise(corCW = cor(meanCountsPerDay_2017, MATSimCycleway, method = "pearson"),
            corAll = cor(meanCountsPerDay_2017, MATSimAll, method = "pearson"))
res$cor2017 <- c(temp$corCW, temp$corAll)

#2022
res$cor2022 <- c(cor(bikeCounterComparison$meanCountsPerDay_2022, bikeCounterComparison$MATSimCycleway, method = "pearson"),
                 cor(bikeCounterComparison$meanCountsPerDay_2022, bikeCounterComparison$MATSimAll, method = "pearson"))


## calculate GEH ##
GEH2017 <- bikeCounterComparison %>% 
  filter(is.na(meanCountsPerDay_2017)==F) %>% 
  group_by(counter_site, MATSimName) %>% 
  summarise(GEHCW = sqrt((2*(MATSimCycleway-meanCountsPerDay_2017)^2)/MATSimCycleway+meanCountsPerDay_2017),
            GEHAll = sqrt((2*(MATSimAll-meanCountsPerDay_2017)^2)/MATSimAll+meanCountsPerDay_2017))

GEH2022 <- bikeCounterComparison %>%  
  group_by(counter_site, MATSimName) %>% 
  summarise(GEHCW = sqrt((2*(MATSimCycleway-meanCountsPerDay_2022)^2)/MATSimCycleway+meanCountsPerDay_2022),
            GEHAll = sqrt((2*(MATSimAll-meanCountsPerDay_2022)^2)/MATSimAll+meanCountsPerDay_2022))


## prepare values to be kept / given back to main script ##
resBikeCounterComparison <- res
rm(res, temp)





## give out results ##

#table:
#writexl::write_xlsx(bikeCounterComparison, path = paste0(RUNNAME, "/", "ANALYSIS_realBikeCounterComparison.xlsx"))

#SSEs
#writexl::write_xlsx(res, path = paste0(RUNNAME, "/", "ANALYSIS_realBikeCounterComparisonStatistics.xlsx"))
