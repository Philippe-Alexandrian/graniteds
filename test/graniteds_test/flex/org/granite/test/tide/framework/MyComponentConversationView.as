/**
 * Generated by Gas3 v1.1.0 (Granite Data Services) on Sat Jul 26 17:58:20 CEST 2008.
 *
 * WARNING: DO NOT CHANGE THIS FILE. IT MAY BE OVERRIDDEN EACH TIME YOU USE
 * THE GENERATOR. CHANGE INSTEAD THE INHERITED CLASS (Contact.as).
 */

package org.granite.test.tide.framework {
	
	import org.fluint.uiImpersonation.UIImpersonator;
	

	[Name(scope="conversation")]
    public class MyComponentConversationView {
    	
    	[In(create="true")]
    	public var view1:MySparkViewConv1;
    	
    	[Observer("initConv")]
    	public function initConv():void {
			UIImpersonator.addChild(view1);
    	}
    }
}