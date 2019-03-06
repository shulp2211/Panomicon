"use strict";
/* identifiers for cystoscape display panels */
const MAIN_ID = 0; // left-side
const SIDE_ID = 1; // right-side
const BOTH_ID = 2; // both panels, used for intersection

/* these are the main and side graphs - as Cytoscape objects */
var vizNet = [null, null];
/* these are also the Graphs - using Network structure */
var toxyNet = [null, null];

/**   API USED BY TOXYGATES TO HANDLE THE CONNECTION TO VISUALIZATION **/
/**
 * Initialize the visualization display.
 * Method called by Toxygates to start the visualization of a graph. A single
 * panel is added to applications DOM and a cytoscape object is associated to it
 */
function onReadyForVisualization(){
  /* capture the parent node for the visualization panel */
  $("#display")
    /* add a new panel to display a graph */
    .append('<div id="leftDisplay" class="sub-viz"></div>')
    .ready(function(){
      var left = $("#leftDisplay");
      /* set a data field for the element */
      left.data("idx", MAIN_ID);
      /* add a new cytoscape element to the <div> */
      initCytoscapeGraph(MAIN_ID, left);
    })
    /* Append modal dialogs to the visualization panel in the DOM */
    .append($('#colorScaleDialog'))
    ;
}

/**
 * Initialize a cytoscape object
 * Init a new instance of cytoscape graph with default display properties for
 * nodes and edges, and associates it with the specified DOM container.
 * Each cytoscape is also associated listeners to handle the display of pop-up
 * information and context menus.
 *
 * @param {int} id The id of the display panel being initialized.
 * @param {DOM Node} container The DOM element (usually a <div>) that will be
 * used as container for a cytoscape element.
 */
function initCytoscapeGraph(id, container){
  /* init the object as a cytoscape instance, and associate it to the provided
   * container */
  vizNet[id] = cytoscape({
    container: container,
    styleEnabled: true,
  });
  /* set default style for nodes and edges in the network */
  vizNet[id].initStyle();

  /* set default behaviour for mouse hover on nodes */
  vizNet[id].on("mouseover", "node", onNodeEnter);
  /* set default behaviour for mouse hovering out of nodes */
  vizNet[id].on("mouseout", "node", onNodeExit);

  /* add a context menu to the panel, and position it within the DOM at a level
   * where events will be captured */
  vizNet[id].initContextMenu(id);
  $(".ctx-menu-"+id)
    .appendTo($(".gwt-DialogBox")[0])
    .hide();

  /* add behaviour to the selection and unselections of nodes */
  vizNet[id].on("select", "node", onNodeSelection);
  vizNet[id].on("unselect", "node", onNodeUnselection);

  /* assign graph data to the cystocape object */
  changeNetwork(id);
}

/**
 * Show the appropriate layout depending on the panel selected by the user. If
 * both panels are selected, a null layout is selected.
 * Hide nodes classed as 'hidden'
 */
$(document).on("change", "#panelSelect", function(){
  /* Capture the panel selected by the user */
  let id = parseInt($("#panelSelect").val());
  switch(id){
    /* single panel selection */
    case MAIN_ID:
    case SIDE_ID:
      /* Display the appropriate layout */
      let layout = (toxyNet[id].layout !== "null" ? toxyNet[id].layout.options.name : "null");
      $("#layoutSelect").val(layout);

      /* Enable checkbox and toggle to the appropriate display of hidden nodes */
      $("#showHiddenNodesCheckbox").attr("disabled",false);
      if( toxyNet[id].hidden.length > 0 )
        $("#showHiddenNodesCheckbox").prop("checked",false);
      else
        $("#showHiddenNodesCheckbox").prop("checked",true);
      break;
    /* dual panel selection */
    case BOTH_ID:
      /* Default layout set to None */
      $("#layoutSelect").val('null');
      /* Disable use of checkbox for hidden nodes */
      $("#showHiddenNodesCheckbox").attr("disabled",true);
      break;
  }
});

/**
 * Applies the selected layout, to the currently active graph. In case the
 * option "Both" has been selected, the layout is applied ONLY to the
 * intersection of the networks.
 */
$(document).on("change", "#layoutSelect", function (){
  /* Capture the panel selected by the user */
  let id = parseInt($("#panelSelect").val());
  /* Retrieve the name of the selected layout */
  let name = $("#layoutSelect").find(":selected").val();
  /* We only apply a layout if an option different to None has been selected*/
  if( name !== "null" ){
    switch(id){
      /* Apply layout to all nodes in a single panel */
      case MAIN_ID:
      case SIDE_ID:
        toxyNet[id].layout = vizNet[id].updateLayout(name);
        toxyNet[id].layout.run();
        break;
      /* Apply layout to intersecting nodes in both panels */
      case BOTH_ID:
        /* Selected layout is applied to intersecting nodes, whilst a
         * complementary layout is applied to other nodes in both panels */
        vizNet[MAIN_ID].dualLayout(vizNet[SIDE_ID], name, "grid");
        /* set the layout for both networks as a default 'None' */
        toxyNet[MAIN_ID].layout = vizNet[MAIN_ID].layout({name: 'null'});
        toxyNet[SIDE_ID].layout = vizNet[MAIN_ID].layout({name: 'null'});
        break;
    }
  }
});

/**
 * Hide/show from the visualiation all nodes in a graph that are labeled as
 * 'hidden'. Initially, only unconnected nodes are hidden.
 * TODO - Provide the user with a way to hide nodes of the graph.
 */
$(document).on("change", "#showHiddenNodesCheckbox", function(){
  /* Capture the panel selected by the user */
  let id = parseInt($("#panelSelect").val());
  /* Determine if the hidden nodes should be shown or not */
  let showHidden = $("#showHiddenNodesCheckbox").is(":checked");
  console.log("hiddennodes", id, showHidden);
  switch(id){
    /* Hide/show 'hidden' nodes of a single panel */
    case MAIN_ID:
    case SIDE_ID:
      toxyNet[id].hidden = vizNet[id].showHiddenNodes(showHidden, toxyNet[id].hidden);
      console.log("en showhidden", toxyNet[id].hidden);
      /* Fit the visualization to the current viewport */
      vizNet[id].fit();
      break;
    /* Dual panel operation should be unavailable */
    case BOTH_ID:
      /* nothing to be done */
      break;
  }
  /* trigger the change in layout, as the network potentially changed */
  $("#layoutSelect").trigger("change");
});

/**
 * Highlight, on both panels, the intersection (equal nodes) of both networks
 * currently on display
 */
$(document).on("change", "#showIntersectionCheckbox", function(){
  let hlgh = $("#showIntersectionCheckbox").is(":checked");
  if( hlgh )
    vizNet[MAIN_ID].toogleHighlight(vizNet[SIDE_ID], hlgh, nodeColor.HIGHLIGHT);
  else{
    vizNet[MAIN_ID].setDefaultStyle(vizNet[MAIN_ID].elements());
    vizNet[SIDE_ID].setDefaultStyle(vizNet[SIDE_ID].elements());
  }
});

/**
 * Merge the graphs shown on left and right panels to a single structure,
 * keeping a single copy of elements that intersect.
 */
$(document).on("click", "#mergeNetworkButton", function(){
  /* Remove DOM elements for the right SIDE_ID */
  removeRightDisplay();

  /* The hidden nodes in the merging network is simply the union of the hidden
  * nodes in the original networks */
  toxyNet[MAIN_ID].hidden = toxyNet[MAIN_ID].hidden.union(toxyNet[SIDE_ID].hidden);
  /* Trigger an event to show hidden nodes if required */
  /* Make sure "hidden" nodes remain hidden */
  let showHidden = $("#showHiddenNodesCheckbox").prop("checked", false);
  $("#showHiddenNodesCheckbox").trigger("change");

  let layout = $("#layoutSelect").find(":selected").val();

  /* Perform the merge of the networks */
  vizNet[MAIN_ID].mergeWith(vizNet[SIDE_ID].elements(), layout);
  /* Set a default preset layout for the merged network */
  toxyNet[MAIN_ID].layout = vizNet[MAIN_ID].layout({name: 'null'});

  /* Clean un-required variables */
  vizNet[SIDE_ID] = null;
  toxyNet[SIDE_ID] = null;

  /* Fit the new network to the viewport */
  vizNet[MAIN_ID].resize();
  vizNet[MAIN_ID].fit();
});

/** ---------------------- UPDATE NODE MODAL ---------------------------- **/
/**
 * Apply a color scale to a network
 * Once the user has selected the relevant options from the corresponding dialog,
 * the color coming from a linear transformation of white to color is used to
 * update the display of all nodes in the network.
 */
$(document).on("click", "#okColorScaleDialog", function (event){
  /* identify the graph on to appply the color scale */
  let id  = $("#colorScaleDialog").data('id');

  /* retrieve the selected weights for both msgRNA and microRNA */
  let msgWgt = $("#msgRNAWeight").val();
  let micWgt = $('#microRNAWeight').val();

  /* find the minimum and maximum values for the selected weights */
  let min = vizNet[id].nodes().min(function(node){
    if (node.data('type') === nodeType.MSG_RNA )
      return node.data('weight')[msgWgt];
    return node.data('weight')[micWgt];
  }).value;
  let max = vizNet[id].nodes().max(function(node){
    if (node.data('type') === nodeType.MSG_RNA )
      return node.data('weight')[msgWgt];
    return node.data('weight')[micWgt];
  }).value;

  /* apply a color to each node in the dataset that is linearly interpolated
   * between white (min) and user selected color (max) */
  let baseColor = $("#scaleColor").val();
  vizNet[id].nodes().forEach(function(ele){
    /* retrieve the current node's weight value */
    let val = ele.data('weight')[msgWgt];
    if (ele.data('type') === nodeType.MICRO_RNA )
      val = ele.data('weight')[micWgt];

    if (val !== NaN){
      /* define the color for the node's weight value */
      let c = valueToColor(val, min, max, min, "#FFFFFF", baseColor);
      /* assing the color to the node, using the base color for the node's border */
      if( c !== "#aNaNaN" ){
        ele.data("color", c);
        ele.data('borderColor', baseColor);
      }
    }
  });

  /* hide the modal after color has been applied to nodes */
  $("#colorScaleDialog").css('visibility', 'hidden');
});

/**
 * Hide a modal dialog.
 * the corresponding modal when the close option is selected.
 * No changes are applied and whatever information the user added to the modal
 * components is lost.
 */
$(document).on("click", ".cancelBtn", function(event){
  let dialog = $(event.target).data().dialog;
  $("#"+dialog).css('visibility', 'hidden');
});

/**
 * Handle updates made on a node through the corresponding modal. Once the user
 * selects to update, we check each of the node's properties, and whenever we
 * find any change, we register them on the corresponding instance in the graph
 */
$(document).on("click", "#updateNodeModal #updateNode", function(event){
  var id = $("#updateNodeModal").data("idx");
   /* reference to the node currently selected on the visualization */
   var node = vizNet[id].nodes().getElementById($("#nodeID").val());
   /* nodeID is not to be changed by the user */
   /* nodeLabel */
   var label = $("#updateNodeModal #nodeLabel").val();
   node.data("label", label);

   /* nodeType */
   var type = $("#updateNodeModal #nodeType").val()
   node.data("type", type);

   /* nodeWeights */
   var selection = $("#updateNodeModal #nodeWeights").val();
   if( selection !== null ){
     var w = node.data("weight");
     w[selection] = $("#updateNodeModal #weightValue").val();
     node.data("weight", w);
   }

   /* nodeColor */
   var color = $("#updateNodeModal #nodeColor").val();
   node.data("color", color);
   node.style("background-color", color);

   /* nodeShape */
   var shape = $("#updateNodeModal #nodeShape").val();
   node.style("shape", shape);

   /* hide the modal */
   var modal = $(event.target).data().modal;
   $("#"+modal).hide();
 });

 /**
  * Whenever the user changes the type of a node, default values for color and
  * shape are displayed.
  */
$(document).on("change", "#updateNodeModal #nodeType", function(evt){
  // get the text from the currently selected option
  var type = $("#nodeType :selected").text();
  // update default color for this type of node
  $("#nodeColor").val(nodeColor[type]);
  // update default shape for this type of node
  $("#nodeShape").val(nodeShape[type]);
});

 /**
  * Handle the display of the correct value when the user chooses a different
  * weight from available list.
  */
$(document).on("change", "#updateNodeModal #nodeWeights", function(evt){
  var node = vizNet.nodes().getElementById($("#nodeID").val());
  var selection = $("#nodeWeights").val();
  if( selection !== null )
    $("#weightValue").val(node.data()["weight"][selection]);
  else
    $("#weightValue").val("");
});

/** ---------------------------- SEARCH NODE  ---------------------------- **/
/**
 * Handle the search of a particular node within the network.
 */
$(document).on("click", "#searchNodeModal #searchNodes", function(evt){
  // nothing to do if there is no network
  var id = $("#panelSelect").val();
  if( vizNet[id] === null ) return;

  // retrieve the search string
  var label = $("#searchNodeModal #nodeLabel").val();
  // select the corresponding nodes within the graph
  var selection = vizNet[id].nodes('[label*="'+label+'"]');
  selection.select();

  // once all nodes with matching labels have been selected, hide the modal
  var modal = $(event.target).data().modal;
  $("#"+modal).hide();
});



/** ------------------------------------------------------------------ **/
/**          Required methods for toxygates integration                **/
/** ------------------------------------------------------------------ **/

/**
* Enable a dual panel visualization, by adding an extra DOM component. The
* extra component is only added once, so we need to double check that the panel
* is not already there, before creating it.
*/
function showNetworkOnRight() {
  /* Check if there is already a right panel */
  let right = $("#rightDisplay");
  if( right.length !== 0 ){
    initCytoscapeGraph(SIDE_ID, right);
    return;
  }

  // Have the left-panel reduce its size to half of the available display
  $("#leftDisplay").addClass("with-side");
  // Define the new right-side panel, together with its elements, and add it
  // to the DOM
  $("#display")
    .append('<div id="rightDisplay" class="sub-viz"></div>')
    .ready(function(){
      let right = $("#rightDisplay");
      initCytoscapeGraph(SIDE_ID, right);

      /* set interface controls for dual panel visualization */
      updateInterfaceControls(2);

      /* Fit the left graph to the smaller viewport */
      vizNet[MAIN_ID].resize();
      vizNet[MAIN_ID].fit();
  });
}

/**
 * Called by Toxygates on an already running network visualization dialog when
 * it wants to switch to a different network, which has been placed in
 * window.toxyNet.
 * Method used to set the network to be displayed in either the main or side
 * display.
 * It is automatically called by Toxygates on load of a new visualization, or
 * by the user in the case of upgrading the side display only.
 * @param {int} id whether the network should be added to the main display
 * (id == 0) or to the side display (id == 1)
 */
function changeNetwork(id=MAIN_ID){
  /* convertedNetwork is the object where toxygates stores the network, using a
   * JSON style string */
  toxyNet[id] = new Network(convertedNetwork["title"],
    convertedNetwork["interactions"],
    convertedNetwork["nodes"]
  );

  /* add the loaded network to the corresponding display and do the necesary
   * transformations to fit the graph to the current display size */
  vizNet[id].elements().remove(); // remove all previous elements
  vizNet[id].add(toxyNet[id].getCytoElements()); // add new ones
  vizNet[id].hideUnconnected(); // mark unconnected nodes as hidden

  /* set a default layout for the network */
  toxyNet[id].layout = vizNet[id].layout({name:"null"});
  toxyNet[id].hidden = vizNet[id].showHiddenNodes();

  /* trigger the display or hidding of hidden nodes */
  setTimeout(function(){
    $("#panelSelect").val(id);
    $("#panelSelect").trigger("change");

    $("showHiddenNodesCheckbox").prop("checked", false);
  }, 0);
  /* fit the graph to the current viewport */
  vizNet[id].fit();
}

/**
 * Handle selection of nodes on the complementary display, in order to provide
 * a paired visualization.
 * @param {any} event the selection event triggered on the original display.
 * Notice that in the event of multiple selection, an event is triggered for
 * each newly selected element.
 */
function onNodeSelection(event){
  // The id of the DOM element where the selection was triggered
  var dpl = event.cy.container().id;
  // Definition of the complementary display panel
  var otherID = (dpl === "leftDisplay") ? 1 : 0;
  // If the complementary display is empty, we don't need to do anything
  if( vizNet[otherID] !== null ){
    // Target node - the node that was selected
    var n = event.target;
    // Select the corresponding node on the complementary display (element with
    // the same id). If no such node exists, the nothing will happen
    vizNet[otherID].nodes('[id="'+n.id()+'"]').select();
  }
}

/**
 * Handle the de-selection of nodes on the complementary display, in order to
 * provide a paired visualization.
 * @param {any} event the un-selection event triggered on the original display.
 */
function onNodeUnselection(event){
  // The id of the DOM element where the unselection was triggered
  var dpl = event.cy.container().id;
  // Definition of the complementary display panel
  var otherID = (dpl === "leftDisplay") ? 1 : 0;
  // If the complementary display is empty, we don't need to do anything
  if( vizNet[otherID] !== null ){
    // Target node - the node that was unselected
    var n = event.target;
    // Un-select the corresponding node on the complementary display (element
    // with the same id)
    vizNet[otherID].nodes('[id="'+n.id()+'"]').unselect();
  }
}

/**
 * Handle the definition of a pop-up div element, to be shown whenever the user
 * hovers over a node on the network.
 *
 * @param{any} event The mouseover event triggered when the user hovers over a
 * node on the display.
 */
function onNodeEnter(event){
  // console.log("event", event);
  // retrieve the node element that triggered the event
  let node = event.cy.$(event.target);
  // console.log("node", node);
  let popup = node.popper({
    content: ()=>{
      let div = document.createElement('div');
      div.classList.add('popper');

      let t = document.createElement('table');
      let idRow = t.insertRow();
      let cell = idRow.insertCell(0);
      cell.appendChild(document.createTextNode('Probe'))
      cell = idRow.insertCell(1);
      cell.appendChild(document.createTextNode(node.data("id")));

      idRow = t.insertRow();
      cell = idRow.insertCell(0);
      cell.appendChild(document.createTextNode('Type'))
      cell = idRow.insertCell(1);
      cell.appendChild(document.createTextNode(node.data("type")));

      idRow = t.insertRow();
      cell = idRow.insertCell(0);
      cell.appendChild(document.createTextNode('Symbol'))
      cell = idRow.insertCell(1);
      // cell.appendChild(document.createTextNode(node.data("symbol")[0]));

      div.appendChild(t);
      document.body.appendChild(div);
      return div;
    },
    popper: {},
  });

  node.on('position', function(){popup.scheduleUpdate();});
}

/**
 * Handle the removal of the pop-up for a node
 */
function onNodeExit(event){
  let node = event.target;
  node.removeListener('position');
  $(".popper").remove();
}

/**
 * Called by Toxygates to get the desired height, in pixels, of the user
 * interaction div
 */
function uiHeight(){
  return 92;
}

/**
 * Updates toxyNet with the changes made to vizNet
 */
function updateToxyNet(){
  var title = toxyNet["title"];
  var nodes = vizNet[MAIN_ID].getToxyNodes();
  var edges = vizNet[MAIN_ID].getToxyInteractions();

  toxyNet[MAIN_ID] = new Network(title, edges, nodes);
}
