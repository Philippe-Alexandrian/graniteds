/*
 *   GRANITE DATA SERVICES
 *   Copyright (C) 2006-2015 GRANITE DATA SERVICES S.A.S.
 *
 *   This file is part of the Granite Data Services Platform.
 *
 *   Granite Data Services is free software; you can redistribute it and/or
 *   modify it under the terms of the GNU Lesser General Public
 *   License as published by the Free Software Foundation; either
 *   version 2.1 of the License, or (at your option) any later version.
 *
 *   Granite Data Services is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser
 *   General Public License for more details.
 *
 *   You should have received a copy of the GNU Lesser General Public
 *   License along with this library; if not, write to the Free Software
 *   Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301,
 *   USA, or see <http://www.gnu.org/licenses/>.
 */
package org.granite.test.tide.framework {
	
	import org.granite.tide.events.TideContextEvent;
	

	[Name("myComponentObserver")]
	[Bindable]
    public class MyComponentObserver {
    	
    	public var untypedEventWithContext:Boolean = false;
    	public var untypedEventWithArgs:Boolean = false;
    	public var untypedEventArg1:String = null;
    	public var untypedEventArg2:Boolean = false;
    	public var typedEvent:int = 0;
    	public var multipleObserverEvent2:Boolean = false;
    	public var multipleObserverEvent3:Boolean = false;
		public var multipleObserverEvent2b:Boolean = false;
		public var multipleObserverEvent3b:Boolean = false;
    	
    	
    	[Observer]
    	public function myHandler(event:MyEvent):void {
    		typedEvent++;
    	}
    	
    	[Observer("someEvent2")]
    	public function myHandler2(event:TideContextEvent):void {
    		untypedEventWithContext = true;
    	}
    	
    	[Observer("someEvent3")]
    	public function myHandler3(arg1:String, arg2:Boolean):void {
    		untypedEventWithArgs = true;
    		untypedEventArg1 = arg1;
    		untypedEventArg2 = arg2;
    	}
    	
    	[Observer("someEvent2")]
    	[Observer("someEvent3")]
    	public function myHandler4(event:TideContextEvent):void {
    		if (event.type == "someEvent2")
    			multipleObserverEvent2 = true;
    		else if (event.type == "someEvent3")
    			multipleObserverEvent3 = true;
    	}
		
		[Observer("someEvent2b, someEvent3b")]
		public function myHandler5(event:TideContextEvent):void {
			if (event.type == "someEvent2b")
				multipleObserverEvent2b = true;
			else if (event.type == "someEvent3b")
				multipleObserverEvent3b = true;
		}
    }
}
