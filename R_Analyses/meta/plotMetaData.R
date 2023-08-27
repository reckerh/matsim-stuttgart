library(ggplot2)
library(tidyverse)

DCNAME <- "PF_10pct_cont"
OLDNAME <- "PF_10pct_cont"




##### 0 - read data #####

DCModeStats <- read.table(paste0("DC_", DCNAME, "/modestats.txt"), sep = "\t", header = T)
OLDModeStats <- read.table(paste0("Old_", OLDNAME, "/modestats.txt"), sep = "\t", header = T)

DCScoreStats <- read.table(paste0("DC_", DCNAME, "/scorestats.txt"), sep = "\t", header = T)
OLDScoreStats <- read.table(paste0("Old_", OLDNAME, "/scorestats.txt"), sep = "\t", header = T)





##### 1 - Plot Mode Stats #####

## combine datasets ##
DCModeStats$ScoreType <- "neuer Scoring-Ansatz"
OLDModeStats$ScoreType <- "bisheriger Scoring-Ansatz"

ModeStats <- rbind(DCModeStats, OLDModeStats)


## transform data into long data ##
ModeStats <- ModeStats %>% 
  pivot_longer(cols = c(bike, car, freight, pt, ride, walk), names_to = "mode")


## plot datasets ##
ggplot(ModeStats) +
  geom_line(aes(x = Iteration, y = value*100, colour = mode), size = 1) +
  facet_wrap(vars(ScoreType)) +
  theme_bw() +
  #theme(panel.grid = element_blank()) + 
  labs(y = "Modal Split-Anteil [%]", colour = "Modus") +
  scale_colour_manual(
    breaks = c("bike", "car", "freight", "pt", "ride", "walk"),
    labels = c("Fahrrad", "MIV (Fahrer)", "Frachtverkehr", "ÖPV", "MIV (Mitfahrer)", "zu Fuß"),
    values = c("chartreuse2", "red", "darkgrey", "blue", "orange", "lightblue")
  )
ggsave(file = paste0("DC_",DCNAME,"--","OLD_",OLDNAME,"_modeStats.png"), width = 9, height = 7)





##### 2 - Plot Score Stats #####

## combine datasets ##
DCScoreStats$ScoreType <- "neuer Scoring-Ansatz"
OLDScoreStats$ScoreType <- "bisheriger Scoring-Ansatz"

ScoreStats <- rbind(DCScoreStats, OLDScoreStats)


## transform data into long data ##
ScoreStats <- ScoreStats %>% 
  pivot_longer(cols = c(avg..EXECUTED, avg..WORST, avg..AVG, avg..BEST), names_to = "score")


## plot datasets ##
ggplot(ScoreStats) +
  geom_line(aes(x = ITERATION, y = value, colour = score), size = 1) +
  facet_wrap(vars(ScoreType)) +
  theme_bw() +
  #ylim(0, 9600) +
  #theme(panel.grid = element_blank()) + 
  labs(y = "Score", colour = "Score-Durchschnitt") +
  scale_colour_manual(
    breaks = c("avg..AVG", "avg..BEST", "avg..EXECUTED", "avg..WORST"),
    labels = c("Durchschnitt des Durchschnitts der Pläne", "Durchschnitt der besten Pläne", "Durchschnitt der ausgeführten Pläne", "Durchschnitt der schlechtesten Pläne"),
    values = c("dodgerblue4", "chartreuse2", "gold", "coral3")
  )
ggsave(file = paste0("DC_",DCNAME,"--","OLD_",OLDNAME,"_scoreStats.png"), width = 9, height = 7)
