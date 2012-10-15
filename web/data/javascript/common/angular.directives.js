'use strict';

angular.module('jquery-ui.directives', [])
.directive('jqueryUiAutocomplete', function() {
	return function(scope, element, attrs) {
		var elemName = attrs.name;
		var expression = attrs.jqueryUiAutocomplete;
		scope.$watch(expression, function(value) {
			var items = this.$eval(expression);
			element
				.autocomplete({
					source : items,
					html : false,
					select : function( event, ui ) {
						element.trigger('input');
					},
					change : function( event, ui ) {
						element.trigger('input');
					}
				})
				.blur(function() {
					element.trigger('input');
				});
		});
	};
});

angular.module('ds.directives', [])
.directive('dsAutocomplete', function() {
	return function(scope, element, attrs) {
		var elemName = attrs.name;
		var items = null;
		if( attrs.dsAutocomplete == "users" ) {
			items = DsAutoComplete.users;
		}

		element
			.autocomplete({
				source : items,
				html : true,
				select : function( event, ui ) {
					element.trigger('input');
				},
				change : function( event, ui ) {
					element.trigger('input');
				}
			})
			.blur(function() {
				element.trigger('input');
			});
	};
})
.directive('dsPopup', ['PopupService', function(PopupService) {
	return {
		restrict : 'A',
		link : function(scope, element, attrs) {
			var popupOptions = {
				title : attrs.dsPopupTitle
			};
			element.bind("click", function() {
				PopupService.load("data/cltemplates/"+attrs.dsPopup, scope, popupOptions);
			});
		}
	};
}])
.factory('PopupService', ['$http', '$compile', function($http, $compile) {
	var popupService = {
		popupElement : null
	};

	popupService.getPopup = function(create) {
		if( popupService.popupElement == null && create) {
			popupService.popupElement = $('<div class="modal hide gfxbox"></div>');
			popupService.popupElement.appendTo('body');
		}

		return popupService.popupElement;
	}

	popupService.load = function(url, scope, options) {
		$http.get(url).success(
			function(data) {
				var popup = popupService.getPopup(true);
				popup.html(data);
				$compile(popup)(scope);
				$(popup).dialog(options);
			});
	}

	popupService.close = function() {
		var popup = popupService.getPopup(false);
		if( popup != null ) {
			$(popup).dialog('hide');
		}
	}

	return popupService;
}])
.factory('dsChartSupport', function() {
	return {
		__chartId : 1,
		generateChartId : function() {
			return "_dsChart"+this.__chartId++;
		},
		parseAxis : function(element, axisName, target) {
			var axisElem = element.find(axisName);
			if( axisElem.size() > 0 ) {
				if( axisElem.attr('label') ) {
					target.label = axisElem.attr('label');
				}
				if( axisElem.attr('pad') ) {
					target.pad = axisElem.attr('pad');
				}
				if( axisElem.attr('tick-interval') ) {
					target.tickInterval = axisElem.attr('tick-interval');
				}
			}
		},
		parseLegend : function(element, target) {
			var legendElem = element.find('legend');
			if( legendElem.size() > 0 ) {
				target.show = true;
				if( legendElem.attr('location') ) {
					target.location = legendElem.attr('location');
				}
			}
		},
		generateDataFromScopeValue : function(options, evalValue) {
			var data = [];
			angular.forEach(evalValue, function(entry) {
				if( typeof entry === 'object' && !(entry instanceof Array)) {
					var seriesEntry = {};
                    seriesEntry.label = entry.label;
					data.push(entry.data);
					options.series.push(seriesEntry);
				}
				else {
					data.push(entry);
				}
			});
			return data;
		}
	};
})
.directive('dsLineChart', ['dsChartSupport', function(dsChartSupport) {
	return {
		restrict : 'A',
		link : function(scope, element, attrs) {
			var currentId = dsChartSupport.generateChartId();
			element.attr('id', currentId);
			
			attrs.$observe('dsLineChart', function(value) {
				scope.$watch(value, function(evalValue) {
					if( evalValue == null || !evalValue ) {
						evalValue = [null];
					}

					var options = {
						axes : {
							xaxis : {},
							yaxis : {}
						},
						legend: {},
						highlighter:{show:true, sizeAdjust: 7.5},
						cursor:{show:false},
						series: []
					};
					if( attrs.title ) {
						options.title = attrs.title;
					}

					dsChartSupport.parseAxis(element, 'x-axis', options.axes.xaxis);
					dsChartSupport.parseAxis(element, 'y-axis', options.axes.yaxis);
					dsChartSupport.parseLegend(element, options.legend);

					var data = dsChartSupport.generateDataFromScopeValue(options, evalValue);

					$('#'+currentId).find('canvas,div').remove();
					DS.plot(currentId, data, options);
				});
			});
		}
	};
}])
.directive('dsPieChart', ['dsChartSupport', function(dsChartSupport) {
	return {
		restrict : 'A',
		link : function(scope, element, attrs) {
			var currentId = dsChartSupport.generateChartId();
			element.attr('id', currentId);

			attrs.$observe('dsPieChart', function(value) {
				scope.$watch(value, function(evalValue) {
					if( evalValue == null || !evalValue ) {
						evalValue = [null];
					}

					var options = {
						seriesDefaults : {
							renderer:$.jqplot.PieRenderer,
							rendererOptions:{
								showDataLabels:true,
								dataLabelThreshold:0
							}
						},
						legend: {},
						highlighter:{
							show:false
						},
						cursor:{show:false},
						series: []
					};
					if( attrs.title ) {
						options.title = attrs.title;
					}
					if( attrs.labelThreshold ) {
						options.seriesDefaults.rendererOptions.dataLabelThreshold =
							attrs.labelThreshold;
					}
					if( !attrs.percentValues ) {
						options.seriesDefaults.rendererOptions.dataLabels='value';
					}

					dsChartSupport.parseLegend(element, options.legend);

					var data = dsChartSupport.generateDataFromScopeValue(options, evalValue);

					$('#'+currentId).find('canvas,div').remove();
					DS.plot(currentId, data, options);
				});
			});
		}
	};
}])
.factory('dsGraphSupport', function() {
	/* Basiert auf Dracula Graph Library */
	var Graph = {};
	Graph.Layout = {};
	Graph.Layout.Spring = function(graph) {
		this.graph = graph;
		this.iterations = 500;
		this.maxRepulsiveForceDistance = 7;
		this.minAttractiveForceDistance = 3;
		this.k = 2;
		this.c = 0.01;
		this.maxVertexMovement = 0.5;
		this.layout();
	};
	Graph.Layout.Spring.prototype = {
		layout: function() {
			this.layoutPrepare();
			for (var i = 0; i < this.iterations; i++) {
				this.layoutIteration();
			}
			this.layoutCalcBounds();
		},

		layoutPrepare: function() {
			for (var i in this.graph.nodes) {
				var node = this.graph.nodes[i];
				node.layoutPosX = 0;
				node.layoutPosY = 0;
				node.layoutForceX = 0;
				node.layoutForceY = 0;
			}

		},

		layoutCalcBounds: function() {
			var minx = Infinity, maxx = -Infinity, miny = Infinity, maxy = -Infinity;

			for (var i in this.graph.nodes) {
				var x = this.graph.nodes[i].layoutPosX;
				var y = this.graph.nodes[i].layoutPosY;

				if(x > maxx) maxx = x;
				if(x < minx) minx = x;
				if(y > maxy) maxy = y;
				if(y < miny) miny = y;
			}

			this.graph.layoutMinX = minx;
			this.graph.layoutMaxX = maxx;
			this.graph.layoutMinY = miny;
			this.graph.layoutMaxY = maxy;
		},

		layoutIteration: function() {
			// Forces on nodes due to node-node repulsions

			var prev = new Array();
			for(var c in this.graph.nodes) {
				var node1 = this.graph.nodes[c];
				for (var d in prev) {
					var node2 = this.graph.nodes[prev[d]];
					this.layoutRepulsive(node1, node2);

				}
				prev.push(c);
			}

			// Forces on nodes due to edge attractions
			for (var i = 0; i < this.graph.edges.length; i++) {
				var edge = this.graph.edges[i];
				this.layoutAttractive(edge);
			}

			// Move by the given force
			for (i in this.graph.nodes) {
				var node = this.graph.nodes[i];
				var xmove = this.c * node.layoutForceX;
				var ymove = this.c * node.layoutForceY;

				var max = this.maxVertexMovement;
				if(xmove > max) xmove = max;
				if(xmove < -max) xmove = -max;
				if(ymove > max) ymove = max;
				if(ymove < -max) ymove = -max;

				node.layoutPosX += xmove;
				node.layoutPosY += ymove;
				node.layoutForceX = 0;
				node.layoutForceY = 0;
			}
		},

		layoutRepulsive: function(node1, node2) {
			if (typeof node1 == 'undefined' || typeof node2 == 'undefined')
				return;
			var dx = node2.layoutPosX - node1.layoutPosX;
			var dy = node2.layoutPosY - node1.layoutPosY;
			var d2 = dx * dx + dy * dy;
			if(d2 < 0.01) {
				dx = 0.1 * Math.random() + 0.1;
				dy = 0.1 * Math.random() + 0.1;
				var d2 = dx * dx + dy * dy;
			}
			var d = Math.sqrt(d2);
			if(d < this.maxRepulsiveForceDistance) {
				var repulsiveForce = this.k * this.k / d;
				node2.layoutForceX += repulsiveForce * dx / d;
				node2.layoutForceY += repulsiveForce * dy / d;
				node1.layoutForceX -= repulsiveForce * dx / d;
				node1.layoutForceY -= repulsiveForce * dy / d;
			}
		},

		layoutAttractive: function(edge) {
			var node1 = edge.source;
			var node2 = edge.target;

			var dx = node2.layoutPosX - node1.layoutPosX;
			var dy = node2.layoutPosY - node1.layoutPosY;
			var d2 = dx * dx + dy * dy;
			if(d2 < 0.01) {
				dx = 0.1 * Math.random() + 0.1;
				dy = 0.1 * Math.random() + 0.1;
				var d2 = dx * dx + dy * dy;
			}
			var d = Math.sqrt(d2);
			if(d > this.maxRepulsiveForceDistance) {
				d = this.maxRepulsiveForceDistance;
				d2 = d * d;
			}
			else if( d < this.minAttractiveForceDistance ) {
				d = this.minAttractiveForceDistance;
				d2 = d * d;
			}
			var attractiveForce = (d2 - this.k * this.k) / this.k;
			if(edge.attraction == undefined) edge.attraction = 1;
			attractiveForce *= Math.log(edge.attraction) * 0.5 + 1;

			node2.layoutForceX -= attractiveForce * dx / d;
			node2.layoutForceY -= attractiveForce * dy / d;
			node1.layoutForceX += attractiveForce * dx / d;
			node1.layoutForceY += attractiveForce * dy / d;
		}
	};
	return Graph;
})
.directive('dsGraph', ['dsGraphSupport',function(dsGraphSupport) {
	var uid               = ['0', '0', '0'];
	/**
	 * A consistent way of creating unique IDs in angular. The ID is a sequence of alpha numeric
	 * characters such as '012ABC'. The reason why we are not using simply a number counter is that
	 * the number string gets longer over time, and it can also overflow, where as the the nextId
	 * will grow much slower, it is a string, and it will never overflow.
	 *
	 * @returns an unique alpha-numeric string
	 *
	 * Basierend auf angular.nextUid (interne API)
	 */
	function nextUid() {
		var index = uid.length;
		var digit;

		while(index) {
			index--;
			digit = uid[index].charCodeAt(0);
			if (digit == 57 /*'9'*/) {
				uid[index] = 'A';
				return uid.join('');
			}
			if (digit == 90  /*'Z'*/) {
				uid[index] = '0';
			} else {
				uid[index] = String.fromCharCode(digit + 1);
				return uid.join('');
			}
		}
		uid.unshift('0');
		return uid.join('');
	}
	/**
	 * Computes a hash of an 'obj'.
	 * Hash of a:
	 *  string is string
	 *  number is number as string
	 *  object is either result of calling $$hashKey function on the object or uniquely generated id,
	 *         that is also assigned to the $$hashKey property of the object.
	 *
	 * @param obj
	 * @returns {string} hash string such that the same input will have the same hash string.
	 *         The resulting string key is in 'type:hashKey' format.
	 *  Basierend auf angular.hashKey (interne API)
	 */
	function hashKey(obj) {
		var objType = typeof obj,
			key;

		if (objType == 'object' && obj !== null) {
			if (typeof (key = obj.$$hashKey) == 'function') {
				// must invoke on object to keep the right this
				key = obj.$$hashKey();
			} else if (key === undefined) {
				key = obj.$$hashKey = nextUid();
			}
		} else {
			key = obj;
		}

		return objType + ':' + key;
	}
	/**
	 * A map where multiple values can be added to the same key such that they form a queue.
	 * @returns {HashQueueMap}
	 * Basierend auf angular.HashQueueMap (interne API)
	 */
	function HashQueueMap() {}
	HashQueueMap.prototype = {
		push: function(key, value) {
			var array = this[key = hashKey(key)];
			if (!array) {
				this[key] = [value];
			} else {
				array.push(value);
			}
		},

		shift: function(key) {
			var array = this[key = hashKey(key)];
			if (array) {
				if (array.length == 1) {
					delete this[key];
					return array[0];
				} else {
					return array.shift();
				}
			}
		},
		get: function(key) {
			var array = this[key = hashKey(key)];
			return array[0];
		}
	};

	function prepareGraphEdges(graph) {
		for( var i= 0,length=graph.edges.length;i < length; i++) {
			var edge = graph.edges[i];
			if( angular.isObject(edge.source) ) {
				continue;
			}
			// Die Referenz auf die Nodes ist als ID kodiert
			// - nun durch die entsprechenden Nodes ersetzen
			for( var j= 0, length2=graph.nodes.length; j < length2; j++ ) {
				var node = graph.nodes[j];
				if( node.id === edge.source ) {
					edge.source = node;
					if( angular.isObject(edge.target) ) {
						break;
					}
				}
				if( node.id === edge.target ) {
					edge.target = node;
					if( angular.isObject(edge.source) ) {
						break;
					}
				}
			}
			// Falls eine ID nicht aufgeloesst werden konnte
			// die entsprechende Node entfernen
			if( !angular.isObject(edge.source) || !angular.isObject(edge.target) ) {
				graph.edges.splice(i, 1);
				i--;
				length--;
			}
		}
	}

	var graphId = 1;
	// Basierend auf ng-repeat
	return {
		transclude: 'element',
		priority: 1000,
		terminal: true,
		compile: function(element, attr, linker) {
			return function(scope, iterStartElement, attr){
				var graphWidth = parseInt(attr.graphWidth)-parseInt(attr.nodeWidth);
				var graphHeight = parseInt(attr.graphHeight)-parseInt(attr.nodeHeight);
				var curGraphId = graphId++;

				var lastOrder = new HashQueueMap();
				var jsPlumbInstance = jsPlumb.getInstance();
				jsPlumbInstance.importDefaults({
					Connector : [ "Bezier", { curviness:30 } ],
					DragOptions : { cursor: "pointer", zIndex:2000 },
					PaintStyle : { strokeStyle:'darkgreen', lineWidth:2 },
					EndpointStyle : { radius:3, fillStyle:'darkgreen' },
					HoverPaintStyle : {strokeStyle:"#ec9f2e" },
					EndpointHoverStyle : {fillStyle:"#ec9f2e" }
				});
				var arrowCommon = { foldback:0.7, fillStyle:'darkgreen', width:14 };
				var overlays = [
					[ "Arrow", { location:0.8 }, arrowCommon ],
					[ "Arrow", { location:0.2, direction:-1 }, arrowCommon ]
				];
				var uniqueEdges = {};
				scope.$watch(function(scope){
					var graph = $.extend(scope.$eval(attr.dsGraph), {});
					prepareGraphEdges(graph);
					var layout = new dsGraphSupport.Layout.Spring(graph);

					var index, length,
						collection = layout.graph.nodes,
						collectionLength = collection.length,
						childScope,
						nextOrder = new HashQueueMap(),
						key, value,
						array = collection,
						last,
						cursor = iterStartElement;

					var layoutWidth = layout.graph.layoutMaxX-layout.graph.layoutMinX;
					var layoutHeight = layout.graph.layoutMaxY-layout.graph.layoutMinY;

					// we are not using forEach for perf reasons (trying to avoid #call)
					for (index = 0, length = array.length; index < length; index++) {
						key = (collection === array) ? index : array[index];
						value = collection[key];
						last = lastOrder.shift(value);
						if (last) {
							// if we have already seen this object, then we need to reuse the
							// associated scope/element
							childScope = last.scope;
							nextOrder.push(value, last);

							if (index === last.index) {
								// do nothing
								cursor = last.element;
							} else {
								// existing item which got moved
								last.index = index;
								// This may be a noop, if the element is next, but I don't know of a good way to
								// figure this out,  since it would require extra DOM access, so let's just hope that
								// the browsers realizes that it is noop, and treats it as such.
								cursor.after(last.element);
								cursor = last.element;
							}
						} else {
							// new item which we don't know about
							childScope = scope.$new();
						}

						childScope.node = value;
						childScope.$index = index;

						childScope.$first = (index === 0);
						childScope.$last = (index === (collectionLength - 1));
						childScope.$middle = !(childScope.$first || childScope.$last);

						if (!last) {
							uniqueEdges[hashKey(value)] = {};

							linker(childScope, function(clone){
								var id = "_dsGraph"+curGraphId+"i"+index;
								clone.attr("id", id);
								var x = (value.layoutPosX-layout.graph.layoutMinX)/layoutWidth*graphWidth;
								var y = (value.layoutPosY-layout.graph.layoutMinY)/layoutHeight*graphHeight;
								clone.css({
									'left': x+"px",
									'top': y+"px"
								});
								jsPlumbInstance.draggable(clone);

								cursor.after(clone);
								last = {
									scope: childScope,
									element: (cursor = clone),
									index: index,
									elementId : id

								};
								nextOrder.push(value, last);
							});
						}
					}

					for (index = 0, length = layout.graph.edges.length; index < length; index++) {
						var edge = layout.graph.edges[index];
						var node1 = nextOrder.get(edge.source);
						var node2 = nextOrder.get(edge.target);
						if( hashKey(edge.source) === hashKey(edge.target) ) {
							continue;
						}
						if( !uniqueEdges[hashKey(edge.source)][hashKey(edge.target)] ) {
							jsPlumbInstance.connect({
								source:node1.element,
								target:node2.element,
								anchor:"Continuous"
								//overlays:overlays
							});
							uniqueEdges[hashKey(edge.source)][hashKey(edge.target)] = true;
							uniqueEdges[hashKey(edge.target)][hashKey(edge.source)] = true;
						}
					}

					//shrink children
					for (key in lastOrder) {
						if (lastOrder.hasOwnProperty(key)) {
							array = lastOrder[key];
							while(array.length) {
								value = array.pop();
								jsPlumbInstance.detachAllConnections(value.element);
								value.element.remove();
								value.scope.$destroy();
								delete uniqueEdges[key];
							}
						}
					}

					lastOrder = nextOrder;
				});
			};
		}
	};
}]);
