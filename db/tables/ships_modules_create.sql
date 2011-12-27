CREATE TABLE `ships_modules` (
  `id` int(10) NOT NULL default '0',
  `modules` text NOT NULL,
  `nametype` varchar(40) NOT NULL default '',
  `nickname` varchar(30) NOT NULL default '',
  `picture` varchar(100) NOT NULL default '',
  `ru` int(11) NOT NULL default '0',
  `rd` int(11) NOT NULL default '0',
  `ra` int(11) NOT NULL default '0',
  `rm` int(11) NOT NULL default '0',
  `eps` int(11) NOT NULL default '0',
  `cost` int(11) NOT NULL default '0',
  `hull` int(11) NOT NULL default '0',
  `panzerung` tinyint(3) unsigned NOT NULL default '0',
  `ablativeArmor` int(11) unsigned NOT NULL default '0',
  `cargo` mediumint(8) unsigned NOT NULL default '0',
  `nahrungcargo` int(11) NOT NULL default '0',
  `heat` int(11) NOT NULL default '0',
  `crew` int(11) NOT NULL default '0',
  `weapons` text NOT NULL,
  `maxheat` text NOT NULL,
  `torpedodef` tinyint(3) unsigned NOT NULL default '0',
  `shields` int(11) NOT NULL default '0',
  `size` int(11) NOT NULL default '0',
  `jdocks` int(11) NOT NULL default '0',
  `adocks` int(11) NOT NULL default '0',
  `sensorrange` tinyint(4) NOT NULL default '0',
  `hydro` int(11) NOT NULL default '0',
  `deutfactor` tinyint(4) NOT NULL default '0',
  `recost` smallint(5) unsigned NOT NULL default '0',
  `flags` text NOT NULL,
  `werft` int(11) NOT NULL default '0',
  `ow_werft` smallint(6) NOT NULL default '0',
  `srs` tinyint NOT NULL default '1',
  `scanCost` INT NOT NULL DEFAULT '0',
  `pickingCost` INT NOT NULL DEFAULT '0',
  `minCrew`int NOT NULL default '0',
  `lostInEmpChance` double NOT NULL default '0.75',
  `version` int(10) unsigned not null default '0',
  `maxunitsize` tinyint (4) NOT NULL default '1',
  `unitspace` int (11) NOT NULL default '0',
  `versorger` tinyint(1) NOT NULL default '0',
  `bounty` bigint(20) unsigned NOT NULL default '0',
  PRIMARY KEY  (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='modifizierte Schiffstypenwerte fuer "jedes" Schiff'; 
