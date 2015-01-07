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
/**
 * Generated by Gas3 v1.1.0 (Granite Data Services) on Sat Jul 26 17:58:20 CEST 2008.
 *
 * WARNING: DO NOT CHANGE THIS FILE. IT MAY BE OVERRIDDEN EACH TIME YOU USE
 * THE GENERATOR. CHANGE INSTEAD THE INHERITED CLASS (Person.as).
 */

package org.granite.test.tide.data {

    import flash.utils.IDataInput;
    import flash.utils.IDataOutput;
    
    import mx.collections.ListCollectionView;
    
    import org.granite.math.BigInteger;
    import org.granite.meta;
    import org.granite.ns.tide;
    import org.granite.tide.IEntityManager;
    import org.granite.tide.IPropertyHolder;
	import org.granite.test.tide.AbstractEntity;

    use namespace meta;
    use namespace tide;

    [Managed]
    [RemoteClass(alias="org.granite.test.tide.Person7")]
    public class Person7 extends AbstractEntity {

		private var _bigInt:BigInteger;
        private var _bigInts:ListCollectionView;
        
        
        public function set bigInt(value:BigInteger):void {
        	_bigInt = value;
        }
        public function get bigInt():BigInteger {
        	return _bigInt;
        }
        
        public function set bigInts(value:ListCollectionView):void {
            _bigInts = value;
        }
        public function get bigInts():ListCollectionView {
            return _bigInts;
        }

        override meta function merge(em:IEntityManager, obj:*):void {
            var src:Person7 = Person7(obj);
            super.meta::merge(em, obj);
            if (meta::isInitialized()) {
                em.meta_mergeExternal(src._bigInt, _bigInt, null, this, 'bigInt', function setter(o:*):void{_bigInt = o as BigInteger}) as BigInteger;
				em.meta_mergeExternal(src._bigInts, _bigInts, null, this, 'bigInts', function setter(o:*):void{_bigInts = o as ListCollectionView}) as ListCollectionView;
            }
        }

        override public function readExternal(input:IDataInput):void {
            super.readExternal(input);
            if (meta::isInitialized()) {
                _bigInt = input.readObject() as BigInteger;
				_bigInts = input.readObject() as ListCollectionView;
            }
        }

        override public function writeExternal(output:IDataOutput):void {
            super.writeExternal(output);
            if (meta::isInitialized()) {
                output.writeObject((_bigInt is IPropertyHolder) ? IPropertyHolder(_bigInt).object : _bigInt);
				output.writeObject((_bigInts is IPropertyHolder) ? IPropertyHolder(_bigInts).object : _bigInts);
            }
        }
    }
}
