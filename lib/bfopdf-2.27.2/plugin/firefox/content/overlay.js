window.addEventListener("load", function() { bfopdfviewer.init(); }, false);

var bfopdfviewer = {
  init: function() {
    // initialization code
    this.initialized = true;
    this.strings = document.getElementById("bfopdfviewer-strings");
    document.getElementById("contentAreaContextMenu").addEventListener("popupshowing", function() { bfopdfviewer.showContextMenu(); }, false);
  },

  showContextMenu: function() {
    // show or hide the menuitem based on what the context menu is on
    // see http://kb.mozillazine.org/Adding_items_to_menus

    document.getElementById("context-bfopdfviewer").hidden = !gContextMenu.onLink; // no link, no menu item
    // see if link is to a PDF file
    if (gContextMenu.onLink) {
       var pdfIncluded = gContextMenu.getLinkURL().toUpperCase().lastIndexOf(".PDF");
       if (pdfIncluded < 0) {
        document.getElementById("context-bfopdfviewer").hidden = true;
       }
    }
  },
  
  // menu iten clicked
  onMenuItemCommand: function(e) {                             
    if (e.length > 0) {
      var ending = e.toUpperCase().lastIndexOf(".PDF");
      if (ending > 0) {
        var url = e.substr(0, ending + 4);
        var url2 = "http://www.bfo.co.uk/pdfviewer.html?pdf=" + encodeURIComponent(url) + "&feature.Menus=false";
        // open window with document in applet
        window.open(url2, '','chrome,centerscreen,resizable=yes,width=600,height=400');
      }
    }                         
  }

};