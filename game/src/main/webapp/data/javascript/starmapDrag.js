var Starmap = function(){

    //maxX = 25*400;
    //maxY = 25*400;
    var system;
    var target;
    var starmap;
    var scanships = {};
    var fieldSize = 25;

    init();

    function init()
        {
            target = document.getElementById("draggable");
            document.querySelector("#starmap-mouse-event-target").addEventListener("click", (e) => onclick(e));
            starmap = document.getElementById("starmap");
            getElementDimensions();

            addEventListener('resize', getElementDimensions);
        }

    var dimensions;
    function getElementDimensions()
    {
        dimensions = {x:parseInt(getComputedStyle(starmap).width) - 2*fieldSize, y:parseInt(getComputedStyle(starmap).height) - 2*fieldSize};
    }

    function elementWidth()
    {
        return dimensions.x;
    }

    function elementHeight()
    {
        return dimensions.y;
    }

    function maxX()
    {
        var width = system.width*fieldSize;
        return Math.max(-fieldSize, width-elementWidth());
    }
    function maxY()
    {
        var height = system.height*fieldSize;
        return Math.max(-fieldSize, height-elementHeight());
    }


    function registerScanship(scanship)
    {
        //var node = document.getElementById("scanfield-" + scanship.shipId);
        /*var scancircle = {x:scanship.location.x, y:scanship.location.y, r:scanship.scanRange+1, id:scanship.shipId };

        if(scanships[scanship.shipId] == null)
        {
            scanships[scanship.shipId] = scancircle;
        }
*/
    }

    function getCurrentViewRectangle()
    {
        /*var left = Math.floor(parseInt(target.style.left)/fieldSize);
        var top = Math.floor(parseInt(target.style.top)/fieldSize);

        var viewRectangle = {x:-left-2, y:-top-2, w:elementWidth()/fieldSize+4, h:elementHeight()/fieldSize+4}
        return viewRectangle;*/
    }

    var lastUnhide;
    function unHidingOnMove()
    {
        if(lastUnhide == null) lastUnhide = Date.now();
        else if(Date.now() - lastUnhide < 50) return;

        var viewRectangle = getCurrentViewRectangle();

        for(const [key, value] of Object.entries(scanships))
        {
            var isVisible = RectCircleColliding(value, viewRectangle);

            if(value.maskNode == undefined)
            {
                value.maskNode = document.getElementById("scanship-" + key);
                if(value.maskNode == null) continue;
            }

            if(!isVisible)
            {
                if(value.maskNode.style.display != "none")
                {
                    value.maskNode.style.display = "none";
                }
                else
                {
                    continue;
                }
            }
            else
            {
                if(value.maskNode.style.display == "none")
                {
                    value.maskNode.style.display = "block";
                }
                else
                {
                    continue;
                }
            }

            if(value.fieldsNode == undefined)
            {
                value.fieldsNode = document.getElementById("scanfield-" + key);
                if(value.fieldsNode == null) continue;
            }

            if(!isVisible)
            {
                if(value.fieldsNode.style.display != "none")
                {
                    value.fieldsNode.style.display = "none";
                }
            }
            else
            {
                if(value.fieldsNode.style.display == "none")
                {
                    value.fieldsNode.style.display = "block";
                }
            }
        }
    }

    function RectCircleColliding(circle,rect){
        var distX = Math.abs(circle.x - rect.x-rect.w/2);
        var distY = Math.abs(circle.y - rect.y-rect.h/2);

        if (distX > (rect.w/2 + circle.r)) { return false; }
        if (distY > (rect.h/2 + circle.r)) { return false; }

        if (distX <= (rect.w/2)) { return true; }
        if (distY <= (rect.h/2)) { return true; }

        var dx=distX-rect.w/2;
        var dy=distY-rect.h/2;
        return (dx*dx+dy*dy<=(circle.r*circle.r));
    }

    document.body.addEventListener("mousedown", function (e) {
        if (e.target &&
            e.target.classList.contains("dragme")) {
            startDrag(e);
            // handle event here
        }
    });

    function startDrag(e) {
        // determine event object
        if (!e) {
            var e = window.event;
        }

        // IE uses srcElement, others use target

        if (target.className != 'dragme') {
            return
        };
        // calculate event X, Y coordinates
        offsetX = e.clientX;
        offsetY = e.clientY;

        coordX = lastX;
        coordY = lastY;
        drag = true;

        // move div element
        onmousemove = document.onmousemove;
        document.onmousemove = dragDiv;
    }
    var onmousemove;

    function dragDiv(e) {
        if (!drag) {
            return
        };
        if (!e) {
            var e = window.event
        };

        // move div element

        var newX = coordX + e.clientX - offsetX
        var newY = coordY + e.clientY - offsetY;

        setPosition(newX, newY);
        return false;
    }

    var lastX=0;
    var lastY=0;
    var legendTargetsX;
    var legendTargetsY;
    function setPosition(newX, newY)
    {
        //var targ = document.getElementById("draggable");

        newX = Math.min(0, Math.max(newX, -maxX()));
        newY = Math.min(0, Math.max(newY, -maxY()));

        lastX = newX;
        lastY = newY;

        target.style.transform = "translate("+ newX + "px, " + newY + "px)";
        //targ.style.left = newX;
        //targ.style.top = newY;

        if(legendTargetsX == null) legendTargetsX = document.querySelectorAll(".scroll-x");
        if(legendTargetsY == null) legendTargetsY = document.querySelectorAll(".scroll-y");

        legendTargetsX[0].style.transform = "translate(" + newX + "px, 0px)";
        legendTargetsX[1].style.transform = "translate(" + newX + "px, 0px)";
        legendTargetsY[0].style.transform = "translate(0px, " + newY + "px)";
        legendTargetsY[1].style.transform = "translate(0px, " + newY + "px)";

        //unHidingOnMove();
    }

    function getPixelByCoordinates(x)
    {
        return (x-1)*fieldSize;
    }

    function setCoordinates(x, y)
    {
        setPosition(-getPixelByCoordinates(x) + elementWidth()/2, -getPixelByCoordinates(y) + elementHeight() / 2);
        setMarkerToCoordinates(x, y);
    }

    var marker;
    function setMarkerToCoordinates(x, y)
    {
        if(marker == null) marker = document.getElementById("position-marker");
        marker.style.display = "block";
        marker.style.left = getPixelByCoordinates(x) + 'px';
        marker.style.top = getPixelByCoordinates(y) + 'px';
    }

    function onclick(event)
    {
        var y = (event.offsetY - parseInt(document.querySelector("#draggable").style.top));
        var x = (event.offsetX - parseInt(document.querySelector("#draggable").style.left));

        var location = getLocationFromPixels(x, y);
        setMarkerToCoordinates(location.x, location.y);
    }

    function getLocationFromPixels(x, y)
    {
        var offset = getTranslateValues(); //getTranslateValues(target);
        return {x: Math.floor((x-offset.x)/fieldSize)+1, y: Math.floor((y-offset.y)/fieldSize)+1};
    }

    function getTranslateValues() {
        return {
          x: lastX,
          y: lastY,
          z: 0
        };
    }

    function stopDrag() {
        stopUnHiding = true;
        drag = false;
        //lastUnhide = Date.now() - 20;
        //await new Promise(r => setTimeout(r, 51));

        //unHidingOnMove();
        document.onmousemove = onmousemove;
    }

    function setSystem(newSystem)
    {
        system = newSystem;
        scanships = {};
    }

    window.onload = function () {
        document.onmouseup = stopDrag;
    }

    function getSystem()
    {
        return system;
    }


    this.setSystem = setSystem;
    this.setCoordinates = setCoordinates;
    this.onclick = onclick;
    this.setMarkerToCoordinates = setMarkerToCoordinates;
    this.getLocationFromPixels = getLocationFromPixels;
    this.getSystem = getSystem;
    this.registerScanship = registerScanship;
    this.elementWidth = elementWidth;
    this.elementHeight = elementHeight;
};